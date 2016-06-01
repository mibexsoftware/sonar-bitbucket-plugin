package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.config.Settings
import org.sonar.api.issue.{Issue, ProjectIssues}

import scala.collection.JavaConverters._
import scala.collection.mutable


@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class ReviewCommentsCreator(projectIssues: ProjectIssues,
                            bitbucketClient: BitbucketClient,
                            inputFileCache: InputFileCache,
                            pluginConfig: SonarBBPluginConfig,
                            settings: Settings,
                            issuesOnChangedLinesFilter: IssuesOnChangedLinesFilter) extends BatchComponent {
  private val logger = LoggerFactory.getLogger(getClass)

  def createOrUpdateComments(pullRequest: PullRequest,
                             existingReviewComments: Seq[PullRequestComment],
                             pullRequestResults: PullRequestReviewResults): Map[Int, PullRequestComment] = {
    val commentsToBeAdded = processIssues(pullRequest, pullRequestResults)
    val (commentsByPathAndLine, commentsToDelete) = processExistingComments(existingReviewComments)

    commentsToBeAdded foreach { case (file, issuesByLine) =>
      issuesByLine foreach { case (line, issues) =>
        commentsByPathAndLine.get(file) match {
          case Some(commentsByLine) if commentsByLine.contains(line) => // already a comment on this line

            val comment = commentsByPathAndLine(file)(line)

            if (comment.content != issues.toString()) { // only update if comment is not equal to existing one
              updateComment(pullRequest, issues.toString(), comment)
            }

            commentsToDelete.remove(comment.commentId)
          case _ =>
            createComment(pullRequest, file, line, issues.toString())
        }

      }
    }

    commentsToDelete.toMap
  }

  private def processExistingComments(existingReviewComments: Seq[PullRequestComment]) = {
    val commentsByFileAndLine = new mutable.HashMap[String, mutable.Map[Int, PullRequestComment]]()
      .withDefaultValue(new mutable.HashMap[Int, PullRequestComment]())
    val reviewCommentsToBeDeleted = new mutable.HashMap[Int, PullRequestComment]()
    val inlineComments = existingReviewComments filter { _.isInline }
    if (logger.isDebugEnabled) {
      logger.debug(LogUtils.f(s"Found ${inlineComments.size} existing inline comments:"))
      inlineComments foreach { c =>
        logger.debug(LogUtils.f(s"  - ${c.filePath}:${c.line}: ${c.content}"))
      }
    }

    inlineComments foreach { c =>
      reviewCommentsToBeDeleted += c.commentId -> c

      (c.filePath, c.line) match {
        case (Some(path), Some(line)) =>
          commentsByFileAndLine.update(path, commentsByFileAndLine(path) + (line -> c))
        case _ =>
      }
    }

    (commentsByFileAndLine, reviewCommentsToBeDeleted)
  }

  private def processIssues(pullRequest: PullRequest, reviewResults: PullRequestReviewResults) = {
    val issues = collectIssuesInProject()
    // we only take new issues here because for the existing issues we would not get
    // file information in the input file cache sensor
    val onlyNewIssues = issues.filter(_.isNew)
    // because of Bitbucket bug #11925, we cannot create issues on context lines, so we have to filter the issues for
    // new/changed lines to get the correct overall issue count for a pull request
    val issuesOnChangedLines = issuesOnChangedLinesFilter.filter(pullRequest, onlyNewIssues)
    debugLogIssueStatistics(issues, issuesOnChangedLines)
    val onlyIssuesWithMinSeverity = issuesOnChangedLines
      .filter(i => SonarUtils.isSeverityGreaterOrEqual(i, pluginConfig.minSeverity()))
    val commentsToBeAdded = new mutable.HashMap[String, mutable.Map[Int, StringBuilder]]()

    onlyIssuesWithMinSeverity foreach { i =>
      inputFileCache.resolveRepoRelativePath(i.componentKey()) match {

        case Some(repoRelPath) =>
          // file level comments do not have a line number! we use 0 for them here
          val lineNr = Option(i.line()).flatMap(l => Option(l.toInt)).getOrElse(0)

          if (!commentsToBeAdded.contains(repoRelPath)) {
            commentsToBeAdded(repoRelPath) = new mutable.HashMap[Int, StringBuilder]()
          }

          if (!commentsToBeAdded(repoRelPath).contains(lineNr)) {
            commentsToBeAdded(repoRelPath) += lineNr -> new StringBuilder(SonarUtils.sonarMarkdownPrefix())
          }

          commentsToBeAdded(repoRelPath)(lineNr).append("\n\n" + SonarUtils.renderAsMarkdown(i, settings))

          reviewResults.issueFound(i)
        case None =>
          logger.warn(LogUtils.f(s"Could not resolve path for ${i.componentKey()}"))
      }

    }

    commentsToBeAdded.toMap
  }

  private def debugLogIssueStatistics(issues: Seq[Issue], issuesOnChangedLines: Seq[Issue]) {
    if (logger.isDebugEnabled) {
      logger.debug(LogUtils.f(s"Found ${issues.size} issues and ${issues.count(_.isNew)} of them are new:"))
      issues.filter(_.isNew) foreach { i =>
        logger.debug(LogUtils.f(s"  - ${i.componentKey()}:${i.line()}: ${i.message()}"))
      }
      logger.debug(LogUtils.f(s"And ${issuesOnChangedLines.size} of these are on changed or new lines:"))
      issuesOnChangedLines foreach { i =>
        logger.debug(LogUtils.f(s"  + ${i.componentKey()}:${i.line()}: ${i.message()}"))
      }
    }
  }

  private def createComment(pullRequest: PullRequest, filePath: String, line: Int, message: String) {
    if (logger.isDebugEnabled) {
      logger.debug(LogUtils.f(s"Creating new pull request comment for issue in $filePath on line $line: $message"))
    }
    bitbucketClient.createPullRequestComment(
      pullRequest = pullRequest,
      message = message,
      line = Option(line),
      filePath = Option(filePath)
    )
  }

  private def updateComment(pullRequest: PullRequest, message: String, comment: PullRequestComment) {
    bitbucketClient.updateReviewComment(
      pullRequest = pullRequest,
      commentId = comment.commentId,
      message = message
    )
  }

  private def collectIssuesInProject() =
    projectIssues
      .issues()
      .asScala
      .toSeq

}

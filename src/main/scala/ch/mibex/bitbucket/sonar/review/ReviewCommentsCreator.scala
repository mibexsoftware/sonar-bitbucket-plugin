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
    val newIssues = collectNewIssuesInProject()
    val issuesOnChangedLines = issuesOnChangedLinesFilter.filter(pullRequest, newIssues)
    debugLogIssueStatistics(newIssues, issuesOnChangedLines)
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
          logger.debug(LogUtils.f(s"No path resolved for ${i.componentKey()}"))
      }

    }

    commentsToBeAdded.toMap
  }

  private def debugLogIssueStatistics(newIssues: Seq[Issue], issuesOnChangedLines: Seq[Issue]) {
    if (logger.isDebugEnabled) {
      logger.debug(LogUtils.f(s"Found ${newIssues.size} new issues:"))
      newIssues foreach { i =>
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

  private def collectNewIssuesInProject() =
    // with sonar.analysis.mode=preview and sonar.analysis.mode=issues I always get all issues here
    // although I only request new ones; this should be changed with SonarQube 5.4; until then, we still have to filter
    // issues on changed lines only by using the diff from Bitbucket
    projectIssues
      .issues()
      .asScala
      .filter(_.isNew)
      .toSeq

}

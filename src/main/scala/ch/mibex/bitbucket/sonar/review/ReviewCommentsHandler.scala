package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import ch.mibex.bitbucket.sonar.{GitBaseDirResolver, SonarBBPluginConfig}
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.batch.{InstantiationStrategy, ScannerSide}
import org.sonar.api.config.Settings
import org.sonar.api.utils.log.Loggers

import scala.collection.immutable.HashMap
import scala.collection.mutable

@ScannerSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class ReviewCommentsHandler(
  bitbucketClient: BitbucketClient,
  pluginConfig: SonarBBPluginConfig,
  settings: Settings,
  gitBaseDirResolver: GitBaseDirResolver,
  issuesOnChangedLinesFilter: IssuesOnChangedLinesFilter
) {
  private val logger = Loggers.get(getClass)

  def updateComments(
    pullRequest: PullRequest,
    allIssues: Iterable[PostJobIssue],
    existingReviewComments: Seq[PullRequestComment],
    pullRequestResults: PullRequestReviewResults
  ): Unit = {
    val commentsToBeAdded = processIssues(allIssues.toList, pullRequest, pullRequestResults)
    var (commentsByPathAndLine, commentsToDelete) = processExistingComments(existingReviewComments)

    commentsToBeAdded foreach { case (file, issuesByLine) =>
      issuesByLine foreach { case (line, issues) =>
        commentsByPathAndLine.get(file) match {
          case Some(commentsByLine) if commentsByLine.contains(line) => // already a comment on this line
            val comment = commentsByPathAndLine(file)(line)
            if (comment.content != issues.toString()) {
              // only update if comment is not equal to existing one
              updateComment(pullRequest, issues.toString(), comment)
            }
            commentsToDelete -= comment.commentId
          case _ =>
            createComment(pullRequest, file, line, issues.toString())
        }
      }
    }

    deletePreviousComments(pullRequest, commentsToDelete)
  }

  private def deletePreviousComments(pullRequest: PullRequest, commentsToDelete: Map[Int, PullRequestComment]) {
    val sonarComments = commentsToDelete.filter(_._2.content.startsWith(SonarUtils.sonarMarkdownPrefix()))
    sonarComments.keys foreach { commentId =>
      bitbucketClient.deletePullRequestComment(pullRequest, commentId)
    }
  }

  private def processExistingComments(existingReviewComments: Seq[PullRequestComment]) = {
    def debugLog(inlineComments: Seq[PullRequestComment]) = {
      if (logger.isDebugEnabled) {
        logger.debug(LogUtils.f(s"Found ${inlineComments.size} existing inline comments:"))
        inlineComments foreach { c =>
          logger.debug(LogUtils.f(s"  - ${c.filePath}:${c.line}: ${c.content}"))
        }
      }
    }

    val commentsByFileAndLine = mutable.HashMap[String, mutable.Map[Int, PullRequestComment]]()
    var reviewCommentsToBeDeleted = HashMap[Int, PullRequestComment]()
    val inlineComments = existingReviewComments.filter(_.isInline)
    debugLog(inlineComments)

    inlineComments foreach { c =>
      reviewCommentsToBeDeleted += c.commentId -> c

      (c.filePath, c.line) match {
        case (Some(path), Some(line)) =>
          commentsByFileAndLine.getOrElseUpdate(path, mutable.HashMap[Int, PullRequestComment]()) += line -> c
        case _ =>
      }
    }

    (commentsByFileAndLine, reviewCommentsToBeDeleted)
  }

  private def processIssues(issues: Seq[PostJobIssue],
    pullRequest: PullRequest,
    reviewResults: PullRequestReviewResults) = {
    val onlyNewIssues = issues.filter(_.isNew)
    // because of Bitbucket bug #11925, we cannot create issues on context lines, so we have to filter the issues for
    // new/changed lines to get the correct overall issue count for a pull request
    val issuesOnChangedLines = issuesOnChangedLinesFilter.filter(pullRequest, onlyNewIssues)
    debugLogIssueStatistics(issues, issuesOnChangedLines)
    val commentsToBeAdded = new mutable.HashMap[String, mutable.Map[Int, StringBuilder]]()

    onlyIssuesWithMinSeverity(issuesOnChangedLines) foreach { issue =>
      gitBaseDirResolver.getRepositoryRelativePath(issue).foreach(filePath => {
        // file level comments do not have a line number! we use 0 for them here
        val lineNr = Option(issue.line()).flatMap(l => Option(l.toInt)).getOrElse(0)

        if (!(commentsToBeAdded.contains(filePath) && commentsToBeAdded(filePath).contains(lineNr))) {
          val prefix = new StringBuilder(SonarUtils.sonarMarkdownPrefix())
          commentsToBeAdded.getOrElseUpdate(filePath, mutable.HashMap[Int, StringBuilder]()) += lineNr -> prefix
        }

        commentsToBeAdded(filePath)(lineNr).append("\n\n" + SonarUtils.renderAsMarkdown(issue, settings))
        reviewResults.issueFound(issue)
      })
    }

    commentsToBeAdded.toMap
  }

  private def onlyIssuesWithMinSeverity(issuesOnChangedLines: Seq[PostJobIssue]) =
    issuesOnChangedLines
      .filter(i => SonarUtils.isSeverityGreaterOrEqual(i, Severity.valueOf(pluginConfig.minSeverity())))

  private def debugLogIssueStatistics(issues: Seq[PostJobIssue], issuesOnChangedLines: Seq[PostJobIssue]) {
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

}

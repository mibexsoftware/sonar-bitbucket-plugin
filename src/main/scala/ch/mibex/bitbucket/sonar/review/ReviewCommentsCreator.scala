package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.PluginConfiguration
import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.utils.SonarUtils
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.issue.ProjectIssues

import scala.collection.JavaConverters._
import scala.collection.mutable


@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class ReviewCommentsCreator(projectIssues: ProjectIssues,
                            bitbucketClient: BitbucketClient,
                            inputFileCache: InputFileCache,
                            pluginConfig: PluginConfiguration,
                            issuesOnChangedLinesFilter: IssuesOnChangedLinesFilter) extends BatchComponent {

  def createOrUpdateComments(pullRequest: PullRequest,
                             existingReviewComments: Seq[PullRequestComment],
                             pullRequestResults: PullRequestReviewResults) = {
    val commentsToBeAdded = processIssues(pullRequest, pullRequestResults)
    val (commentsByPathAndLine, commentsToDelete) = processExistingComments(existingReviewComments)

    commentsToBeAdded foreach { case (file, issuesByLine) =>
      issuesByLine foreach { case (line, issues) =>

        commentsByPathAndLine.get(file) match {
          case Some(x) if x.contains(line) =>
            val comment = commentsByPathAndLine(file)(line)

            if (comment.content != issues.toString()) {
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
    val commentsToBeAdded = new mutable.HashMap[String, mutable.Map[Int, mutable.StringBuilder]]()

    issuesOnChangedLines foreach { i =>
      if (SonarUtils.isSeverityGreaterOrEqual(i, pluginConfig.minSeverity())) {
        inputFileCache.resolveRepoRelativePath(i.componentKey()) foreach { repoRelPath =>
          // file level comments do not have a line number! we use 0 for them here
          val lineNr = Option(i.line()).flatMap(l => Option(l.toInt)).getOrElse(0)

          if (!commentsToBeAdded.contains(repoRelPath)) {
            commentsToBeAdded(repoRelPath) = new mutable.HashMap[Int, mutable.StringBuilder]()
          }

          if (!commentsToBeAdded(repoRelPath).contains(lineNr)) {
            commentsToBeAdded(repoRelPath) += lineNr -> new mutable.StringBuilder(SonarUtils.sonarMarkdownPrefix())
          }

          commentsToBeAdded(repoRelPath)(lineNr).append("\n\n" + SonarUtils.renderAsMarkdown(i))
        }
      }

      reviewResults.issueFound(i)
    }

    commentsToBeAdded.toMap
  }

  private def createComment(pullRequest: PullRequest, filePath: String, line: Int, message: String) {
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
    projectIssues
      .issues()
      .asScala
      .filter(_.isNew)
      .toSeq

}

package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import org.slf4j.LoggerFactory
import org.sonar.api.batch.{CheckProject, PostJob, SensorContext}
import org.sonar.api.resources.Project


// due to https://jira.sonarsource.com/browse/SONAR-6398, a post job is not called on SonarQube 5.1.0!
class SonarReviewPostJob(bitbucketClient: BitbucketClient,
                         pluginConfig: SonarBBPluginConfig,
                         reviewCommentsUpdater: ReviewCommentsCreator) extends PostJob with CheckProject {
  private val logger = LoggerFactory.getLogger(getClass)

  override def executeOn(project: Project, sensorContext: SensorContext): Unit = {
    findPullRequestsForConfiguredBranch foreach { p =>
      logger.info(LogUtils.f(s"Plug-in is active and will analyze project ${project.getName}"))
      val ourComments = bitbucketClient.findOwnPullRequestComments(p)
      val report = new PullRequestReviewResults(pluginConfig)
      val commentsToDelete = reviewCommentsUpdater.createOrUpdateComments(p, ourComments, report)
      deletePreviousComments(p, commentsToDelete)
      deletePreviousGlobalComments(p, ourComments)
      createGlobalComment(p, report)
      approveOrUnapproveIfEnabled(p, report)
    }
  }

  override def shouldExecuteOnProject(project: Project): Boolean = {
    logger.debug(LogUtils.f("\nPlug-in config: {}\n"), pluginConfig)
    pluginConfig.validate()
  }

  private def findPullRequestsForConfiguredBranch = {
    val branchName = pluginConfig.branchName()
    val pullRequests = bitbucketClient.findPullRequestsWithSourceBranch(branchName)
    if (pullRequests.isEmpty) {
      logger.info(LogUtils.f(
        s"""No open pull requests with source branch '$branchName' found.
           |No analysis will be performed.""".stripMargin.replaceAll("\n", " ")))
    }
    pullRequests
  }

  private def approveOrUnapproveIfEnabled(pullRequest: PullRequest, report: PullRequestReviewResults) {
    if (pluginConfig.approveUnApproveEnabled()) {
      if (report.canBeApproved) {
        bitbucketClient.approve(pullRequest)
      } else {
        bitbucketClient.unApprove(pullRequest)
      }
    }
  }

  private def createGlobalComment(pullRequest: PullRequest, report: PullRequestReviewResults) {
    bitbucketClient.createPullRequestComment(pullRequest = pullRequest, message = report.formatAsMarkdown())
  }

  private def deletePreviousGlobalComments(pullRequest: PullRequest, ownComments: Seq[PullRequestComment]) {
    ownComments
      .filterNot(_.isInline)
      .filter(_.content.startsWith(SonarUtils.sonarMarkdownPrefix()))
      .foreach { c =>
        bitbucketClient.deletePullRequestComment(pullRequest, c.commentId)
      }
  }

  private def deletePreviousComments(pullRequest: PullRequest, commentsToDelete: Map[Int, PullRequestComment]) {
    commentsToDelete foreach { case (commentId, comment) =>
      if (comment.content.startsWith(SonarUtils.sonarMarkdownPrefix())) {
        bitbucketClient.deletePullRequestComment(pullRequest, commentId)
      }
    }
  }

}
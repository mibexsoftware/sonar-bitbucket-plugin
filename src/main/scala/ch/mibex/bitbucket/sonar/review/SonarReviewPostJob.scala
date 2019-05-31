package ch.mibex.bitbucket.sonar.review

import java.net.URLEncoder

import ch.mibex.bitbucket.sonar.client._
import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import org.sonar.api.CoreProperties
import org.sonar.api.batch.postjob.{PostJob, PostJobContext, PostJobDescriptor}
import org.sonar.api.config.Settings
import org.sonar.api.utils.log.Loggers

import scala.collection.JavaConverters._


// due to https://jira.sonarsource.com/browse/SONAR-6398, a post job is not called on SonarQube 5.1.0!
class SonarReviewPostJob(
  bitbucketClient: BitbucketClient,
  pluginConfig: SonarBBPluginConfig,
  reviewCommentsHandler: ReviewCommentsHandler) extends PostJob {
  private val logger = Loggers.get(getClass)

  override def execute(context: PostJobContext): Unit = {
    getPullRequestsToAnalyze foreach { pullRequest =>
      logger.info(LogUtils.f(s"Plug-in is active and will analyze pull request #${pullRequest.id}..."))
      handlePullRequest(context, pullRequest)
    }
  }

  private def getProjectUrl(context: PostJobContext, pullRequest: PullRequest) =
    (getSonarBaseUrl(context.settings()) + "/dashboard?id="
      + context.settings().getString(CoreProperties.PROJECT_KEY_PROPERTY) + ":"
      + URLEncoder.encode(pullRequest.srcBranch, "UTF-8"))

  private def getSonarBaseUrl(settings: Settings) =
    Option(settings.getString(CoreProperties.SERVER_BASE_URL)).getOrElse(settings.getString("sonar.host.url"))

  private def handlePullRequest(context: PostJobContext, pullRequest: PullRequest) {
    setBuildStatus(InProgressBuildStatus, context, pullRequest)
    val ourComments = bitbucketClient.findOwnPullRequestComments(pullRequest)
    val report = new PullRequestReviewResults(pluginConfig)
    reviewCommentsHandler.updateComments(pullRequest, context.issues().asScala, ourComments, report)
    deletePreviousGlobalComments(pullRequest, ourComments)
    createGlobalComment(pullRequest, report)
    approveOrUnapproveIfEnabled(pullRequest, report)
    setBuildStatus(report.calculateBuildStatus(), context, pullRequest)
  }

  private def setBuildStatus(buildStatus: BuildStatus, context: PostJobContext, pullRequest: PullRequest) {
    if (pluginConfig.buildStatusEnabled()) {
      bitbucketClient.updateBuildStatus(pullRequest, buildStatus, getProjectUrl(context, pullRequest))
    }
  }

  private def getPullRequestsToAnalyze =
    if (pluginConfig.pullRequestId() != 0)
      findPullRequestWithConfiguredId(pluginConfig.pullRequestId()).toList
    else findPullRequestsForConfiguredBranch

  private def findPullRequestWithConfiguredId(pullRequestId: Int) = {
    val pullRequest = bitbucketClient.findPullRequestWithId(pullRequestId)
    if (pullRequest.isEmpty) {
      logger.info(LogUtils.f(
        s"""Pull request '$pullRequestId' not found.
            |No analysis will be performed.""".stripMargin.replaceAll("\n", " ")))
    }
    pullRequest
  }

  private def findPullRequestsForConfiguredBranch = {
    val branchName = pluginConfig.branchName()
    val pullRequests = bitbucketClient.findPullRequestsWithSourceBranch(branchName)
    if (pullRequests.isEmpty) {
      logger.info(
        LogUtils.f(
          s"""No open pull requests with source branch '$branchName' found.
              |No analysis will be performed.""".stripMargin.replaceAll("\n", " "))
      )
    }
    pullRequests
  }

  private def approveOrUnapproveIfEnabled(pullRequest: PullRequest, report: PullRequestReviewResults) {
    if (pluginConfig.approveUnApproveEnabled()) {
      if (report.countIssuesWithAboveMaxSeverity == 0) bitbucketClient.approve(pullRequest)
      else bitbucketClient.unApprove(pullRequest)
    }
  }

  private def createGlobalComment(pullRequest: PullRequest, report: PullRequestReviewResults) {
    bitbucketClient.createPullRequestComment(pullRequest, report.formatAsMarkdown())
  }

  private def deletePreviousGlobalComments(pullRequest: PullRequest, ownComments: Seq[PullRequestComment]) {
    ownComments
      .filterNot(_.isInline)
      .filter(_.content.startsWith(SonarUtils.sonarMarkdownPrefix()))
      .foreach { c =>
        bitbucketClient.deletePullRequestComment(pullRequest, c.commentId)
      }
  }

  override def describe(descriptor: PostJobDescriptor): Unit = {
    descriptor
      .name("Sonar Plug-in for Bitbucket Cloud")
      .requireProperties(SonarBBPlugin.BitbucketAccountName, SonarBBPlugin.BitbucketRepoSlug)
  }
}
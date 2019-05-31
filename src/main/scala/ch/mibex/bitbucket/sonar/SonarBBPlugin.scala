package ch.mibex.bitbucket.sonar


import ch.mibex.bitbucket.sonar.client.BitbucketClient
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.review.{PullRequestProjectBuilder, ReviewCommentsHandler, SonarReviewPostJob}
import org.sonar.api.Plugin.Context
import org.sonar.api.{PropertyType, _}
import org.sonar.api.rule.Severity


object SonarBBPlugin {
  final val PluginLogPrefix = "[sonar4bitbucket]"
  final val BitbucketAccountName = "sonar.bitbucket.accountName"
  final val BitbucketRepoSlug = "sonar.bitbucket.repoSlug"
  final val BitbucketTeamName = "sonar.bitbucket.teamName"
  final val BitbucketApiKey = "sonar.bitbucket.apiKey"
  final val BitbucketBranchName = "sonar.bitbucket.branchName"
  final val BitbucketPullRequestId = "sonar.bitbucket.pullRequestId"
  final val SonarQubeIllegalBranchCharReplacement = "sonar.bitbucket.branchIllegalCharReplacement"
  final val SonarQubeMinSeverity = "sonar.bitbucket.minSeverity"
  final val BitbucketOAuthClientKey = "sonar.bitbucket.oauthClientKey"
  final val BitbucketOAuthClientSecret = "sonar.bitbucket.oauthClientSecret"
  final val BitbucketApproveUnapprove = "sonar.bitbucket.approvalFeatureEnabled"
  final val BitbucketBuildStatus = "sonar.bitbucket.buildStatusEnabled"
  final val SonarUnapprovalSeverityLevel = "sonar.bitbucket.maxSeverityApprovalLevel"
}


@Properties(
  Array(
    new Property(
      key = SonarBBPlugin.BitbucketAccountName,
      name = "Bitbucket account name",
      description = "The Bitbucket account your repository belongs to " +
        "(https://bitbucket.org/[account_name]/[repo_slug]).",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketRepoSlug,
      name = "Bitbucket repo slug",
      description = "The slug of your Bitbucket repository (https://bitbucket.org/[account_name]/[repo_slug]).",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketTeamName,
      name = "Bitbucket user name",
      description = "If you want to create pull request comments for Sonar issues with an APP password, " +
        "provide your user name here.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketApiKey,
      name = "Bitbucket APP password",
      description = "If you want to create pull request comments for Sonar issues with an APP password, " +
        "provide the APP password here.",
      `type` = PropertyType.PASSWORD,
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketOAuthClientKey,
      name = "Bitbucket OAuth client key",
      description = "If you want to create pull request comments for Sonar issues under your personal account " +
        "provide the client key of the OAuth consumer created for this application here (needs repository and " +
        "pull request WRITE permissions).",
      `type` = PropertyType.PASSWORD,
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketOAuthClientSecret,
      name = "Bitbucket OAuth client secret",
      description = "If you want to create pull request comments for Sonar issues under your personal account, " +
        "provide the OAuth client secret for this application here.",
      `type` = PropertyType.PASSWORD,
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketBranchName,
      name = "Bitbucket branch name",
      description = "The branch name you want to get analyzed with SonarQube. When building with Jenkins, " +
        "use $GIT_BRANCH. For Bamboo, you can use ${bamboo.repository.git.branch}.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketPullRequestId,
      name = "Bitbucket pull request ID",
      description = "The ID of the pull request you want to get analyzed with SonarQube.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.SonarQubeIllegalBranchCharReplacement,
      name = "SonarQube invalid branch character replacement",
      description = "If you are using SonarQube version <= 4.5, then you have to escape '/' in your branch names " +
        "with another character. Please provide this replacement character here.",
      global = true
    ),
    new Property(
      key = SonarBBPlugin.SonarQubeMinSeverity,
      name = "Min. severity to create pull request comments",
      defaultValue = Severity.MAJOR, // we cannot use default Sonar#defaultSeverity here as this is not a constant value
      description = "Use either INFO, MINOR, MAJOR, CRITICAL or BLOCKER to only have pull request comments " +
        "created for issues with severities greater or equal to this one.",
      global = true
    ),
    new Property(
      key = SonarBBPlugin.BitbucketApproveUnapprove,
      name = "Approve / Unapprove pull request if there are issues with severity >= " +
        SonarBBPlugin.SonarUnapprovalSeverityLevel + ".",
      defaultValue = "true",
      description = "If enabled, the plug-in will approve the pull request if there are no issues with severity >= " +
        SonarBBPlugin.SonarUnapprovalSeverityLevel + ", otherwise it will unapprove the pull request.",
      global = true
    ),
    new Property(
      key = SonarBBPlugin.SonarUnapprovalSeverityLevel,
      name = "The severity level to look for to determine to Auto-Unapprove",
      defaultValue = Severity.CRITICAL,
      description = "If any issues of this level or higher are found, it will unapprove the pull request.",
      global = true
    ),
    new Property(
      key = SonarBBPlugin.BitbucketBuildStatus,
      name = "Bitbucket build status for pull request",
      defaultValue = "true",
      description = "If enabled, the plug-in will update the build status of the pull request depending on the " +
        "Sonar analysis result. The analysis and also the build is failed if there are any critical or blocker issues.",
      global = true
    )
  )
)
class SonarBBPlugin extends Plugin {

  override def define(context: Context): Unit = {
    context.addExtensions(
      classOf[SonarReviewPostJob],
      classOf[SonarBBPluginConfig],
      classOf[PullRequestProjectBuilder],
      classOf[BitbucketClient],
      classOf[ReviewCommentsHandler],
      classOf[IssuesOnChangedLinesFilter],
      classOf[GitBaseDirResolver]
    )
  }

}

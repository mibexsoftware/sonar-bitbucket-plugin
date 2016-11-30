package ch.mibex.bitbucket.sonar

import java.util.{List => JList}

import ch.mibex.bitbucket.sonar.cache.{InputFileCache, InputFileCacheSensor}
import ch.mibex.bitbucket.sonar.client.BitbucketClient
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.review.{GitBaseDirResolver, PullRequestProjectBuilder, ReviewCommentsCreator, SonarReviewPostJob}
import org.sonar.api._
import org.sonar.api.rule.Severity

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer


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
}


@Properties(
  // global = false: do not show these settings in the config page of SonarQube
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
      name = "Bitbucket team ID",
      description = "If you want to create pull request comments for Sonar issues under your team account, " +
        "provide the team ID here.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketApiKey,
      name = "Bitbucket API key",
      description = "If you want to create pull request comments for Sonar issues under your team account, " +
        "provide the API key for your team account here.",
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
      name = "Bitbucket pull request id",
      description = "The id of the pull request you want to get analyzed with SonarQube.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.SonarQubeIllegalBranchCharReplacement,
      name = "SonarQube invalid branch character replacement",
      description = "If you are using SonarQube version <= 4.5, then you have to escape '/' in your branch names " +
        "with another character. Please provide this replacement character here.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.SonarQubeMinSeverity,
      name = "Min. severity to create pull request comments",
      defaultValue = Severity.MAJOR, // we cannot use default Sonar#defaultSeverity here as this is not a constant value
      description = "Use either INFO, MINOR, MAJOR, CRITICAL or BLOCKER to only have pull request comments " +
        "created for issues with severities greater or equal to this one.",
      global = false
    ),
    new Property(
      key = SonarBBPlugin.BitbucketApproveUnapprove,
      name = "Approve / Unapprove pull request if there are critical or blocker issues.",
      defaultValue = "true",
      description = "If enabled, the plug-in will approve the pull request if there are no critical and no " +
        "blocker issues, otherwise it will unapprove the pull request.",
      global = false
    )
  )
)
class SonarBBPlugin extends SonarPlugin {

  override def getExtensions: JList[Object] = {
    ListBuffer(
      classOf[SonarReviewPostJob],
      classOf[SonarBBPluginConfig],
      classOf[PullRequestProjectBuilder],
      classOf[BitbucketClient],
      classOf[InputFileCacheSensor],
      classOf[ReviewCommentsCreator],
      classOf[IssuesOnChangedLinesFilter],
      classOf[GitBaseDirResolver],
      classOf[InputFileCache]
    ).toList
  }

}

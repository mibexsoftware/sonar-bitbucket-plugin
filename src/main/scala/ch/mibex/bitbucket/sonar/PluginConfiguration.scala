package ch.mibex.bitbucket.sonar

import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.config.Settings
import org.sonar.api.rule.Severity

import scala.collection.JavaConverters._

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class PluginConfiguration(settings: Settings) extends BatchComponent {
  private val logger = LoggerFactory.getLogger(getClass)

  def isEnabled: Boolean =
    settings.hasKey(SonarBitbucketPlugin.BitbucketAccountName)

  def accountName(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketAccountName)

  def repoSlug(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketRepoSlug)    

  def teamName(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketTeamName)

  def oauthTokenClientKey(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketOAuthClientKey)

  def oauthTokenClientSecret(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketOAuthClientSecret)

  def apiKey(): String =
    settings.getString(SonarBitbucketPlugin.BitbucketApiKey)

  def minSeverity(): String =
    settings.getString(SonarBitbucketPlugin.SonarQubeMinSeverity)

  def branchName(): String = {
    var branchName = settings.getString(SonarBitbucketPlugin.BitbucketBranchName)
    Option(branchName) foreach { _ =>
      branchName = branchName.replaceFirst("^origin/", "") // Jenkins GIT_BRANCH variable contains a origin/ prefix
      if (branchIllegalCharReplacement().nonEmpty) {
        branchName = branchName.replaceAll("/", branchIllegalCharReplacement())
      }
    }
    branchName
  }

  private def branchIllegalCharReplacement() = // we cannot use "" as defaultValue with SonarQube settings
    Option(settings.getString(SonarBitbucketPlugin.SonarQubeIllegalBranchCharReplacement)).getOrElse("")

  def validate(): Boolean = {
    if (!isEnabled) {
      logger.info(LogUtils.f("Plug-in considered disabled as Bitbucket account name is not configured."))
      return false
    }
    require(isIllegalCharReplacementValid,
      s"Only the following characters are allowed as replacement: ${SonarUtils.ValidIllegalBranchNameReplacementChars}"
    )
    require(Option(repoSlug()).nonEmpty, "A repository slug must be set")
    require(Option(branchName()).nonEmpty, "The branch to analyze must be given")
    require(isValidAuthenticationGiven,
      """Either the name and API key for the Bitbucket team account
        |or an OAuth client key and its secret must be given""".stripMargin.replaceAll("\n", " ")
    )
    Option(minSeverity()) foreach { severity =>
      require(Severity.ALL.asScala.contains(severity), s"Invalid SonarQube severity level $severity")
    }
    true
  }

  private def isIllegalCharReplacementValid =
    SonarUtils.isLegalBranchNameReplacement(branchIllegalCharReplacement())

  private def isValidAuthenticationGiven =
    (Option(teamName()).nonEmpty && Option(apiKey()).nonEmpty) ||
      (Option(oauthTokenClientKey()).nonEmpty && Option(oauthTokenClientSecret()).nonEmpty)

  override def toString: String =
    s"""[PluginConfiguration](
        |accountName=${accountName()},
        |repoSlug=${repoSlug()},
        |teamName=${teamName()},
        |apiKey=${Option(apiKey()).flatMap(s => Option("***")).orNull},
        |oauthTokenClientKey=${Option(oauthTokenClientKey()).flatMap(s => Option("***")).orNull},
        |oauthTokenClientSecret=${Option(oauthTokenClientSecret()).flatMap(s => Option("***")).orNull},
        |branchName=${branchName()},
        |minSeverity=${minSeverity()})""".stripMargin.replaceAll("\n", "")

}
package ch.mibex.bitbucket.sonar

import ch.mibex.bitbucket.sonar.utils.{LogUtils, SonarUtils}
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.config.Settings
import org.sonar.api.platform.Server
import org.sonar.api.rule.Severity

import scala.collection.JavaConverters._

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class SonarBBPluginConfig(settings: Settings, server: Server) extends BatchComponent {
  private val logger = LoggerFactory.getLogger(getClass)

  def isEnabled: Boolean =
    settings.hasKey(SonarBBPlugin.BitbucketAccountName)

  def accountName(): String =
    settings.getString(SonarBBPlugin.BitbucketAccountName)

  def repoSlug(): String =
    settings.getString(SonarBBPlugin.BitbucketRepoSlug)

  def teamName(): String =
    settings.getString(SonarBBPlugin.BitbucketTeamName)

  def oauthTokenClientKey(): String =
    settings.getString(SonarBBPlugin.BitbucketOAuthClientKey)

  def oauthTokenClientSecret(): String =
    settings.getString(SonarBBPlugin.BitbucketOAuthClientSecret)

  def apiKey(): String =
    settings.getString(SonarBBPlugin.BitbucketApiKey)

  def minSeverity(): String =
    settings.getString(SonarBBPlugin.SonarQubeMinSeverity)

  def approveUnApproveEnabled(): Boolean =
    settings.getBoolean(SonarBBPlugin.BitbucketApproveUnapprove)

  def branchName(): String = {
    var branchName = settings.getString(SonarBBPlugin.BitbucketBranchName)
    Option(branchName) foreach { _ =>
      branchName = branchName.replaceFirst("^origin/", "") // Jenkins GIT_BRANCH variable contains a origin/ prefix
      if (branchIllegalCharReplacement().nonEmpty) {
        branchName = branchName.replaceAll("/", branchIllegalCharReplacement())
      }
    }
    branchName
  }

  private def branchIllegalCharReplacement() = // we cannot use "" as defaultValue with SonarQube settings
    Option(settings.getString(SonarBBPlugin.SonarQubeIllegalBranchCharReplacement)).getOrElse("")

  def validate(): Boolean = {
    if (!isEnabled) {
      logger.info(LogUtils.f("Plug-in considered disabled as Bitbucket account name is not configured."))
      return false
    }
    if (server.getVersion.equals("5.1")) {
      logger.error(LogUtils.f("SonarQube v5.1 is not supported because of issue SONAR-6398"))
      return false
    }
    require(isIllegalCharReplacementValid,
      s"""${SonarBBPlugin.PluginLogPrefix} Only the following characters
         |are allowed as replacement: ${SonarUtils.LegalBranchNameReplacementChars}""".stripMargin.replaceAll("\n", " ")
    )
    require(Option(repoSlug()).nonEmpty, s"${SonarBBPlugin.PluginLogPrefix} A repository slug must be set")
    require(Option(branchName()).nonEmpty, s"${SonarBBPlugin.PluginLogPrefix} The branch to analyze must be given")
    require(isValidAuthenticationGiven,
      s"""${SonarBBPlugin.PluginLogPrefix} Either the name and API key for the Bitbucket team account
        |or an OAuth client key and its secret must be given""".stripMargin.replaceAll("\n", " ")
    )
    Option(minSeverity()) foreach { severity =>
      require(Severity.ALL.asScala.contains(severity), s"${SonarBBPlugin.PluginLogPrefix} Invalid severity $severity")
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
        |approveUnApproveEnabled=${approveUnApproveEnabled()},
        |minSeverity=${minSeverity()})""".stripMargin.replaceAll("\n", "")

}
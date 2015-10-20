package ch.mibex.bitbucket.sonar

import org.junit.runner.RunWith
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.platform.Server
import org.sonar.api.rule.Severity
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import org.specs2.specification.Scope


@RunWith(classOf[JUnitRunner])
class PluginConfigurationSpec extends Specification with Mockito {

  class SettingsContext extends Scope {
    val settings = new Settings(new PropertyDefinitions(classOf[SonarBitbucketPlugin]))
    val server = mock[Server]
    val pluginConfig = new PluginConfiguration(settings, server)
  }

  "sonar settings" should {

    "respect default value for Sonar min severity" in new SettingsContext {
      settings.getString(SonarBitbucketPlugin.SonarQubeMinSeverity) must_== Severity.defaultSeverity()
    }

  }

  "plug-in config" should {

    "yield configured account name" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      pluginConfig.accountName() must_== "mibexsoftware"
    }

    "yield configured repository slug" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "test")
      pluginConfig.repoSlug() must_== "test"
    }

    "yield configured team name" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketTeamName, "a_team")
      pluginConfig.teamName() must_== "a_team"
    }

    "yield configured api key" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketApiKey, "1234567890")
      pluginConfig.apiKey() must_== "1234567890"
    }

    "yield configured branch name" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.branchName() must_== "feature/XYZ"
    }

    "yield configured branch name with origin prefix cropped" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "origin/feature/XYZ")
      pluginConfig.branchName() must_== "feature/XYZ"
    }

    "yield configured branch name with replacement character replaced" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBitbucketPlugin.SonarQubeIllegalBranchCharReplacement, "_")
      pluginConfig.branchName() must_== "feature_XYZ"
    }

    "yield configured OAuth client key" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asdfagdfsahdshd")
      pluginConfig.oauthTokenClientKey() must_== "asdfagdfsahdshd"
    }

    "yield configured OAuth client secret" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxxxx")
      pluginConfig.oauthTokenClientSecret() must_== "xxxxxxxxx"
    }

  }

  "plug-in configuration validation" should {

    "consider plug-in as being inactive when account is not set" in new SettingsContext {
      pluginConfig.validate() must beFalse
    }

    "validate for team API based authentication" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketApiKey, "xxxxxxxxx")
      settings.setProperty(SonarBitbucketPlugin.BitbucketTeamName, "a_team")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.validate() must beTrue
    }

    "validate for user OAuth based authentication" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      pluginConfig.validate() must beTrue
    }

    "not accept wrong severity level" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      settings.setProperty(SonarBitbucketPlugin.SonarQubeMinSeverity, "UNKNOWN")
      pluginConfig.validate() must throwA(
        new IllegalArgumentException("requirement failed: Invalid SonarQube severity level UNKNOWN")
      )
    }

    "not accept when no authentication method is configured" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.validate() must throwA(
        new IllegalArgumentException(
          """requirement failed: Either the name and API key for the Bitbucket team account
            |or an OAuth client key and its secret must be given""".stripMargin.replaceAll("\n", " ")
        )
      )
    }

    "not accept when invalid SonarQube branch replacement key is given" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      settings.setProperty(SonarBitbucketPlugin.SonarQubeIllegalBranchCharReplacement, "/")
      pluginConfig.validate() must throwA(
        new IllegalArgumentException(
          """requirement failed: Only the following characters
            |are allowed as replacement: [0-9a-zA-Z:\-_.]*""".stripMargin.replaceAll("\n", " ")
        )
      )
    }

    "not accept when no branch name is given" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      pluginConfig.validate() must throwA(
        new IllegalArgumentException("requirement failed: The branch to analyze must be given")
      )
    }

    "not accept when no repository slug is given" in new SettingsContext {
      settings.setProperty(SonarBitbucketPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBitbucketPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBitbucketPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      pluginConfig.validate() must throwA(
        new IllegalArgumentException("requirement failed: A repository slug must be set")
      )
    }

  }

}

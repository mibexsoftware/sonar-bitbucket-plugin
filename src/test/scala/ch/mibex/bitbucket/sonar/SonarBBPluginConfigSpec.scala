package ch.mibex.bitbucket.sonar

import org.junit.runner.RunWith
import org.sonar.api.config.internal.MapSettings
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.platform.Server
import org.sonar.api.rule.Severity
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope


@RunWith(classOf[JUnitRunner])
class SonarBBPluginConfigSpec extends Specification with Mockito {

  class SettingsContext extends Scope {
    val settings = new MapSettings(new PropertyDefinitions(classOf[SonarBBPlugin]))
    val server = mock[Server]
    val pluginConfig = new SonarBBPluginConfig(settings, server)
  }

  "sonar settings" should {

    "respect default value for Sonar min severity" in new SettingsContext {
      settings.getString(SonarBBPlugin.SonarQubeMinSeverity) must_== Severity.defaultSeverity()
    }

  }

  "plug-in config" should {

    "yield configured account name" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      pluginConfig.accountName() must_== "mibexsoftware"
    }

    "yield configured repository slug" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "test")
      pluginConfig.repoSlug() must_== "test"
    }

    "yield configured team name" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketTeamName, "a_team")
      pluginConfig.teamName() must_== "a_team"
    }

    "yield configured APP password" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketApiKey, "1234567890")
      pluginConfig.apiKey() must_== "1234567890"
    }

    "yield configured branch name" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.branchName() must_== "feature/XYZ"
    }

    "yield configured branch name with origin prefix cropped" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "origin/feature/XYZ")
      pluginConfig.branchName() must_== "feature/XYZ"
    }

    "yield configured branch name with replacement character replaced" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBBPlugin.SonarQubeIllegalBranchCharReplacement, "_")
      pluginConfig.branchName() must_== "feature_XYZ"
    }

    "yield configured pull request id" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketPullRequestId, "123")
      pluginConfig.pullRequestId() must_== 123
    }

    "yield configured OAuth client key" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "asdfagdfsahdshd")
      pluginConfig.oauthTokenClientKey() must_== "asdfagdfsahdshd"
    }

    "yield configured OAuth client secret" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "xxxxxxxxx")
      pluginConfig.oauthTokenClientSecret() must_== "xxxxxxxxx"
    }

    "have approval/unapproval activated by default" in new SettingsContext {
      pluginConfig.approveUnApproveEnabled() must beTrue
    }

    "yield approval/unapproval true setting if enabled" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketApproveUnapprove, "true")
      pluginConfig.approveUnApproveEnabled() must beTrue
    }

    "yield approval/unapproval false setting if disabled" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketApproveUnapprove, "false")
      pluginConfig.approveUnApproveEnabled() must beFalse
    }

  }

  "plug-in configuration validation" should {

    "not allow unsupported SonarQube version 7.7" in new SettingsContext {
      server.getVersion returns "7.7"
      val invalidPluginConfig = new SonarBBPluginConfig(settings, server)
      pluginConfig.validateOrThrow() must throwA(
        new IllegalArgumentException(
          "requirement failed: [sonar4bitbucket] SonarQube v7.7 is not supported because of required preview mode"
        )
      )
    }

    "validate for team API based authentication" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketApiKey, "xxxxxxxxx")
      settings.setProperty(SonarBBPlugin.BitbucketTeamName, "a_team")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.validateOrThrow()
    }

    "validate for user OAuth based authentication" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      pluginConfig.validateOrThrow()
    }

    "not accept wrong severity level" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      settings.setProperty(SonarBBPlugin.SonarQubeMinSeverity, "UNKNOWN")
      pluginConfig.validateOrThrow() must throwA(
        new IllegalArgumentException("[sonar4bitbucket] Invalid severity UNKNOWN")
      )
    }

    "not accept when no authentication method is configured" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      pluginConfig.validateOrThrow() must throwA(
        new IllegalArgumentException(
          """requirement failed: [sonar4bitbucket] Either the user name and APP pasword for your Bitbucket account
            |or an OAuth client key and its secret must be given""".stripMargin.replaceAll("\n", " ")
        )
      )
    }

    "not accept when invalid SonarQube branch replacement key is given" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketBranchName, "feature/XYZ")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      settings.setProperty(SonarBBPlugin.SonarQubeIllegalBranchCharReplacement, "/")
      pluginConfig.validateOrThrow() must throwA(
        new IllegalArgumentException(
          """requirement failed: [sonar4bitbucket] Only the following characters
            |are allowed as replacement: [0-9a-zA-Z:\-_.]*""".stripMargin.replaceAll("\n", " ")
        )
      )
    }

    "not accept when no branch name is given" in new SettingsContext {
      settings.setProperty(SonarBBPlugin.BitbucketAccountName, "mibexsoftware")
      settings.setProperty(SonarBBPlugin.BitbucketRepoSlug, "superrepo")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientKey, "asfasgshhas")
      settings.setProperty(SonarBBPlugin.BitbucketOAuthClientSecret, "xxxxxxx")
      pluginConfig.validateOrThrow() must throwA(
        new IllegalArgumentException(
          "requirement failed: [sonar4bitbucket] The branch to analyze or the pull request ID must be given"
        )
      )
    }

  }

}

package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.{PluginConfiguration, SonarBitbucketPlugin}
import org.junit.runner.RunWith
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class PullRequestResultsSpec extends Specification with Mockito {

  class SettingsContext extends Scope {
    val settings = new Settings(new PropertyDefinitions(classOf[SonarBitbucketPlugin]))
    val pluginConfig = new PluginConfiguration(settings)
  }

  "formatAsMarkdown and canBeApproved" should {

    "yield congrats and approval if no issues are found" in new SettingsContext {
      val results = new PullRequestReviewResults(pluginConfig)
      results.formatAsMarkdown() must_== "**SonarQube Analysis** reported no issues. Take a chocolate :-)"
      results.canBeApproved must beTrue
    }

    "yield issue report for found issues and deny approval due to blocker issue" in new SettingsContext {
      val results = new PullRequestReviewResults(pluginConfig)

      val issue1 = mock[Issue]
      issue1.severity() returns Severity.INFO
      issue1.message() returns "Either remove or fill this block of code."
      issue1.ruleKey() returns RuleKey.parse("squid:S00108")
      results.issueFound(issue1)

      val issue2 = mock[Issue]
      issue2.severity() returns Severity.MAJOR
      issue2.message() returns """Remove this unused "f" local variable."""
      issue2.ruleKey() returns RuleKey.parse("squid:S1481")
      results.issueFound(issue2)

      val issue3 = mock[Issue]
      issue3.severity() returns Severity.BLOCKER
      issue3.message() returns """Remove this "Double.longBitsToDouble" call."""
      issue3.ruleKey() returns RuleKey.parse("squid:S2127")
      results.issueFound(issue3)

      results.formatAsMarkdown() must beEqualTo(
        """**SonarQube Analysis** reported 3 issues:
          |
          |* ![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) 1 blocker
          |* ![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) 1 major
          |* ![INFO](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png) 1 info
          |
          |
          |Watch the comments in this pull request to review them. Note that only issues with severity >=
          |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png)
          |are shown in this pull request.""".stripMargin).ignoreSpace
      results.canBeApproved must beFalse
    }

  }


}
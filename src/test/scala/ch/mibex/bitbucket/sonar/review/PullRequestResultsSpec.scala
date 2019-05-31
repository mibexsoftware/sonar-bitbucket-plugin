package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig}
import org.junit.runner.RunWith
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.config.internal.MapSettings
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.platform.Server
import org.sonar.api.rule.RuleKey
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
@RunWith(classOf[JUnitRunner])
class PullRequestResultsSpec extends Specification with Mockito {

  class SettingsContext extends Scope {
    val settings = new MapSettings(new PropertyDefinitions(classOf[SonarBBPlugin]))
    val server = mock[Server]
    val pluginConfig = new SonarBBPluginConfig(settings, server)
  }

  "formatAsMarkdown and canBeApproved" should {

    "yield congrats and approval if no issues are found" in new SettingsContext {
      val results = new PullRequestReviewResults(pluginConfig)
      results.formatAsMarkdown() must_==
        """**SonarQube Analysis** reported no issues. Take a chocolate :-)
          |
          |Note that only issues with severity >= ![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) (major) are reported.""".stripMargin
      results.countIssuesWithAboveMaxSeverity must_== 0
    }

    "yield issue report for found issues and deny approval due to blocker issue" in new SettingsContext {
      val results = new PullRequestReviewResults(pluginConfig)

      val issue1 = mock[PostJobIssue]
      issue1.severity() returns Severity.INFO
      issue1.message() returns "Either remove or fill this block of code."
      issue1.ruleKey() returns RuleKey.parse("squid:S00108")
      results.issueFound(issue1)

      val issue2 = mock[PostJobIssue]
      issue2.severity() returns Severity.MAJOR
      issue2.message() returns """Remove this unused "f" local variable."""
      issue2.ruleKey() returns RuleKey.parse("squid:S1481")
      results.issueFound(issue2)

      val issue3 = mock[PostJobIssue]
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
          |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) (major)
          |are reported.""".stripMargin).ignoreSpace
      results.countIssuesWithAboveMaxSeverity must_== 1 // default is CRITICAL
    }

  }


}
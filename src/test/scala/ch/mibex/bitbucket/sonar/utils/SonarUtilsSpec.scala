package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.sonar.api.CoreProperties
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.config.Settings
import org.sonar.api.rule.RuleKey
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SonarUtilsSpec extends Specification with Mockito {

  "isSeverityGreaterOrEqual" should {

    "allow severities equal to reference" in {
      val issue = mock[PostJobIssue]
      issue.severity() returns Severity.INFO
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.INFO) must beTrue
    }

    "allow severities greater than reference" in {
      val issue = mock[PostJobIssue]
      issue.severity() returns Severity.MAJOR
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beTrue
    }


    "allow severities greater than reference" in {
      val issue = mock[PostJobIssue]
      issue.severity() returns Severity.MAJOR
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beTrue
    }

    "disallow severities less than reference" in {
      val issue = mock[PostJobIssue]
      issue.severity() returns Severity.INFO
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beFalse
    }

    "fail on unknown severities" in {
      val issue = mock[PostJobIssue]
      issue.severity() returns null
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must throwA[IllegalArgumentException]
    }

  }

  "isLegalBranchNameReplacement" should {

    "allow valid branch char replacements" in {
      SonarUtils.isValidBranchNameReplacement("") must beTrue
      SonarUtils.isValidBranchNameReplacement("3") must beTrue
      SonarUtils.isValidBranchNameReplacement(":-_.") must beTrue
      SonarUtils.isValidBranchNameReplacement("A") must beTrue
    }

    "reject invalid branch char replacements" in {
      SonarUtils.isValidBranchNameReplacement("/") must beFalse
      SonarUtils.isValidBranchNameReplacement("!") must beFalse
    }

  }

  "renderIssue" should {

    "yield the expected Markdown with given server base URL" in {
      val issue = mock[PostJobIssue]
      val settings = mock[Settings]
      issue.severity() returns Severity.INFO
      issue.message() returns "Either remove or fill this block of code."
      issue.ruleKey() returns RuleKey.parse("squid:S00108")
      settings.hasKey(CoreProperties.SERVER_BASE_URL) returns true
      settings.getString(CoreProperties.SERVER_BASE_URL) returns "http://localhost:9000"
      SonarUtils.renderAsMarkdown(issue, settings) must_==
        """![INFO](https://raw.githubusercontent.com/mibexsoftware/
          |sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png) **INFO**: Either remove
          | or fill this block of code. [[Details]](http://localhost:9000/
          |coding_rules#rule_key=squid%3AS00108)""".stripMargin.replaceAll("\n", "")
    }

    "yield the expected Markdown with sonar.host.url" in {
      val issue = mock[PostJobIssue]
      val settings = mock[Settings]
      issue.severity() returns Severity.MAJOR
      issue.message() returns "Check that null is not used"
      issue.ruleKey() returns RuleKey.parse("Scalastyle:org.scalastyle.scalariform.NullChecker")
      settings.hasKey(CoreProperties.SERVER_BASE_URL) returns false
      settings.getString("sonar.host.url") returns "http://mysonar"
      SonarUtils.renderAsMarkdown(issue, settings) must_==
        """![MAJOR](https://raw.githubusercontent.com/mibexsoftware/
          |sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Check that null is not used
          | [[Details]](http://mysonar/coding_rules#rule_key=Scalastyle%3Aorg.scalastyle.scalariform.NullChecker)"""
          .stripMargin.replaceAll("\n", "")
    }

  }

  "getImageMarkdownForSeverity" should {

    "yield Markdown for info severity image" in {
      SonarUtils.toImageMarkdown(Severity.INFO) must_==
        "![INFO](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png)"
    }

    "yield Markdown for minor severity image" in {
      SonarUtils.toImageMarkdown(Severity.MINOR) must_==
        "![MINOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MINOR.png)"
    }

    "yield Markdown for major severity image" in {
      SonarUtils.toImageMarkdown(Severity.MAJOR) must_==
        "![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png)"
    }

    "yield Markdown for critical severity image" in {
      SonarUtils.toImageMarkdown(Severity.CRITICAL) must_==
        "![CRITICAL](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/CRITICAL.png)"
    }

    "yield Markdown for blocker severity image" in {
      SonarUtils.toImageMarkdown(Severity.BLOCKER) must_==
        "![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png)"
    }

  }

}
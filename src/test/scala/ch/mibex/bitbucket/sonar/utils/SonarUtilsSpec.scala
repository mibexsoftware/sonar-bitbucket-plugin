package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SonarUtilsSpec extends Specification with Mockito {

  "isSeverityGreaterOrEqual" should {

    "allow severities equal to reference" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.INFO
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.INFO) must beTrue
    }

    "allow severities greater than reference" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.MAJOR
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beTrue
    }


    "allow severities greater than reference" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.MAJOR
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beTrue
    }

    "disallow severities less than reference" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.INFO
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must beFalse
    }

    "fail on unknown severities" in {
      val issue = mock[Issue]
      issue.severity() returns "UNKNOWN"
      SonarUtils.isSeverityGreaterOrEqual(issue, Severity.MINOR) must throwA[IllegalArgumentException]
    }

  }

  "isLegalBranchNameReplacement" should {

    "allow valid branch char replacements" in {
      SonarUtils.isLegalBranchNameReplacement("") must beTrue
      SonarUtils.isLegalBranchNameReplacement("3") must beTrue
      SonarUtils.isLegalBranchNameReplacement(":-_.") must beTrue
      SonarUtils.isLegalBranchNameReplacement("A") must beTrue
    }

    "reject invalid branch char replacements" in {
      SonarUtils.isLegalBranchNameReplacement("/") must beFalse
      SonarUtils.isLegalBranchNameReplacement("!") must beFalse
    }

  }

  "renderIssue" should {

    "yield the expected Markdown" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.INFO
      issue.message() returns "Either remove or fill this block of code."
      issue.ruleKey() returns RuleKey.parse("squid:S00108")
      SonarUtils.renderAsMarkdown(issue) must_==
        """![INFO](https://raw.githubusercontent.com/mibexsoftware/
          |sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png) Either remove
          | or fill this block of code. [[Details]](http://nemo.sonarqube.org/
          |coding_rules#rule_key=squid%3AS00108)""".stripMargin.replaceAll("\n", "")
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
package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MarkdownUtilsSpec extends Specification with Mockito {

  "renderIssue" should {

    "yield the expected Markdown" in {
      val issue = mock[Issue]
      issue.severity() returns Severity.INFO
      issue.message() returns "Either remove or fill this block of code."
      issue.ruleKey() returns RuleKey.parse("squid:S00108")
      MarkdownUtils.renderIssue(issue) must_==
        """![INFO](https://raw.githubusercontent.com/mibexsoftware/
          |sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png) Either remove
          | or fill this block of code. [[Details]](http://nemo.sonarqube.org/
          |coding_rules#rule_key=squid%3AS00108)""".stripMargin.replaceAll("\n", "")
    }

  }

  "getImageMarkdownForSeverity" should {

    "yield Markdown for info severity image" in {
      MarkdownUtils.getImageMarkdownForSeverity(Severity.INFO) must_==
        "![INFO](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/INFO.png)"
    }

    "yield Markdown for minor severity image" in {
      MarkdownUtils.getImageMarkdownForSeverity(Severity.MINOR) must_==
        "![MINOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MINOR.png)"
    }

    "yield Markdown for major severity image" in {
      MarkdownUtils.getImageMarkdownForSeverity(Severity.MAJOR) must_==
        "![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png)"
    }

    "yield Markdown for critical severity image" in {
      MarkdownUtils.getImageMarkdownForSeverity(Severity.CRITICAL) must_==
        "![CRITICAL](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/CRITICAL.png)"
    }

    "yield Markdown for blocker severity image" in {
      MarkdownUtils.getImageMarkdownForSeverity(Severity.BLOCKER) must_==
        "![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png)"
    }

  }

}
package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.sonar.api.issue.Issue
import org.sonar.api.rule.Severity
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

}
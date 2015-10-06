package ch.mibex.bitbucket.sonar.utils

import org.sonar.api.issue.Issue
import org.sonar.api.rule.Severity
import scala.collection.JavaConverters._

object SonarUtils {
  val ValidIllegalBranchNameReplacementChars = """[0-9a-zA-Z:\-_.]*"""

  def isSeverityGreaterOrEqual(issue: Issue, referenceSeverity: String): Boolean = {
    val severities = Severity.ALL.asScala
    val issueSeverityIdx = severities.indexOf(issue.severity())
    val referenceSeverityIdx = severities.indexOf(referenceSeverity)
    require(issueSeverityIdx >= 0, s"Unknown severity: ${issue.severity()}")
    require(referenceSeverityIdx >= 0, s"Unknown severity: $referenceSeverityIdx")
    issueSeverityIdx >= referenceSeverityIdx
  }

  def isLegalBranchNameReplacement(replacement: String): Boolean =
    replacement.matches(ValidIllegalBranchNameReplacementChars)

}

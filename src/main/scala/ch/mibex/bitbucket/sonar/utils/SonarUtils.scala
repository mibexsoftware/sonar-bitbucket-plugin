package ch.mibex.bitbucket.sonar.utils

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import scala.collection.JavaConverters._
import scala.collection.mutable

object SonarUtils {
  private val SeverityImagesRootUrl =
    """https://raw.githubusercontent.com/mibexsoftware/
      |sonar-bitbucket-plugin/master/src/main/resources/images/severity""".stripMargin.replaceAll("\n", "")
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

  def sonarMarkdownPrefix(): String = "**SonarQube Analysis**"

  def renderAsMarkdown(issue: Issue): String = {
    val ruleLink = getRuleLink(issue.ruleKey())
    val sb = new mutable.StringBuilder()
    sb.append(toImageMarkdown(issue.severity()))
      .append(" ")
      .append(issue.message())
      .append(" ")
      .append(ruleLink)
    sb.toString()
  }

  private def getRuleLink(ruleKey: RuleKey) =
    s"[[Details]](http://nemo.sonarqube.org/coding_rules#rule_key=${encodeForUrl(ruleKey.toString)})"

  private def encodeForUrl(url: String) = {
    try {
      URLEncoder.encode(url, "UTF-8")
    } catch {
      case e: UnsupportedEncodingException =>
        throw new IllegalStateException("Encoding not supported", e)
    }
  }

  def toImageMarkdown(severity: String): String = s"![$severity]($SeverityImagesRootUrl/$severity.png)"

}

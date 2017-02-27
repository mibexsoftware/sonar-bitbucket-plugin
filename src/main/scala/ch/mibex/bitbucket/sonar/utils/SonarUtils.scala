package ch.mibex.bitbucket.sonar.utils

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import ch.mibex.bitbucket.sonar.SonarBBPlugin
import org.sonar.api.CoreProperties
import org.sonar.api.config.Settings
import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import scala.collection.JavaConverters._
import scala.collection.mutable

object SonarUtils {
  val LegalBranchNameReplacementChars = """[0-9a-zA-Z:\-_.]*"""
  private val SeverityImagesRootUrl =
    """https://raw.githubusercontent.com/mibexsoftware/
      |sonar-bitbucket-plugin/master/src/main/resources/images/severity""".stripMargin.replaceAll("\n", "")

  def isSeverityGreaterOrEqual(issue: Issue, referenceSeverity: String): Boolean = {
    val severities = Severity.ALL.asScala
    val issueSeverityIdx = severities.indexOf(issue.severity())
    val referenceSeverityIdx = severities.indexOf(referenceSeverity)
    require(issueSeverityIdx >= 0, s"${SonarBBPlugin.PluginLogPrefix} Unknown severity: ${issue.severity()}")
    require(referenceSeverityIdx >= 0, s"${SonarBBPlugin.PluginLogPrefix} Unknown severity: $referenceSeverityIdx")
    issueSeverityIdx >= referenceSeverityIdx
  }

  def isLegalBranchNameReplacement(replacement: String): Boolean =
    replacement.matches(LegalBranchNameReplacementChars)

  def sonarMarkdownPrefix(): String = "**SonarQube Analysis**"

  def renderAsMarkdown(issue: Issue, settings: Settings): String = {
    val ruleLink = getRuleLink(issue.ruleKey(), settings)
    val sb = new mutable.StringBuilder()
    val severity = issue.severity()
    sb.append(toImageMarkdown(severity))
      .append(s" **$severity**: ")
      .append(issue.message())
      .append(" ")
      .append(ruleLink)
    sb.toString()
  }

  private def getRuleLink(ruleKey: RuleKey, settings: Settings) =
    s"[[Details]](${getSonarUrl(settings)}coding_rules#rule_key=${encodeForUrl(ruleKey.toString)})"

  private def getSonarUrl(settings: Settings) = {
    val baseUrl = if (settings.hasKey(CoreProperties.SERVER_BASE_URL)) {
      settings.getString(CoreProperties.SERVER_BASE_URL)
    } else {
      settings.getString("sonar.host.url")
    }
    if (!baseUrl.endsWith("/")) {
      baseUrl + "/"
    } else {
      baseUrl
    }
  }

  private def encodeForUrl(url: String) = {
    try {
      URLEncoder.encode(url, "UTF-8")
    } catch {
      case e: UnsupportedEncodingException =>
        throw new IllegalStateException(s"${SonarBBPlugin.PluginLogPrefix} Encoding not supported", e)
    }
  }

  def toImageMarkdown(severity: String): String = s"![$severity]($SeverityImagesRootUrl/$severity.png)"

}

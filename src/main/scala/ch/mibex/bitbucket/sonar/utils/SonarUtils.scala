package ch.mibex.bitbucket.sonar.utils

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import ch.mibex.bitbucket.sonar.SonarBBPlugin
import org.sonar.api.CoreProperties
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.config.Settings
import org.sonar.api.rule.RuleKey

import scala.collection.mutable

object SonarUtils {
  final val LegalBranchNameReplacementChars = """[0-9a-zA-Z:\-_.]*"""
  final val SeverityImagesRootUrl =
    """https://raw.githubusercontent.com/mibexsoftware/
      |sonar-bitbucket-plugin/master/src/main/resources/images/severity""".stripMargin.replaceAll("\r?\n", "")

  def isSeverityGreaterOrEqual(issue: PostJobIssue, referenceSeverity: Severity): Boolean = {
    require(Option(issue.severity()).isDefined, s"${SonarBBPlugin.PluginLogPrefix} Unknown severity")
    issue.severity().compareTo(referenceSeverity) >= 0
  }

  def isValidBranchNameReplacement(replacement: String): Boolean =
    replacement.matches(LegalBranchNameReplacementChars)

  def sonarMarkdownPrefix(): String = "**SonarQube Analysis**"

  def renderAsMarkdown(issue: PostJobIssue, settings: Settings): String = {
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

  def toImageMarkdown(severity: Severity): String = s"![$severity]($SeverityImagesRootUrl/$severity.png)"

}

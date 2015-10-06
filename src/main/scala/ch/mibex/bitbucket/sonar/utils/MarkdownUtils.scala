package ch.mibex.bitbucket.sonar.utils

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import org.sonar.api.issue.Issue
import org.sonar.api.rule.RuleKey

import scala.collection.mutable


// see https://bitbucket.org/tutorials/markdowndemo/overview#markdown-header-links
object MarkdownUtils {
  private val ImagesRootUrl =
    """https://raw.githubusercontent.com/mibexsoftware/
      |sonar-bitbucket-plugin/master/src/main/resources/images/severity""".stripMargin.replaceAll("\n", "")

  def sonarPrefix(): String = "**SonarQube Analysis**"

  def renderIssue(issue: Issue): String = {
    val ruleLink = getRuleLink(issue.ruleKey())
    val sb = new mutable.StringBuilder()
    sb.append(getImageMarkdownForSeverity(issue.severity()))
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

  def getImageMarkdownForSeverity(severity: String): String = s"![$severity]($ImagesRootUrl/$severity.png)"

}

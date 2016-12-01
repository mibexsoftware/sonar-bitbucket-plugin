package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import ch.mibex.bitbucket.sonar.utils.{SonarUtils, StringUtils}
import org.sonar.api.issue.Issue
import org.sonar.api.rule.Severity

import scala.collection.JavaConverters._
import scala.collection.mutable


class PullRequestReviewResults(pluginConfiguration: SonarBBPluginConfig) {
  private val newIssuesBySeverity = new mutable.HashMap[String, Int]().withDefaultValue(0)

  def issueFound(issue: Issue): Unit = {
    newIssuesBySeverity(issue.severity()) += 1
  }

  def formatAsMarkdown(): String = {
    val markdown = new StringBuilder()
    markdown.append(s"${SonarUtils.sonarMarkdownPrefix()} reported ")

    newIssuesBySeverity.values.toList match {
      case Nil =>
        markdown.append("no issues. Take a chocolate :-)\n\n")
      case xs =>
        markdown.append(s"${xs.sum} ${StringUtils.pluralise("issue", xs.sum)}:\n\n")
        Severity.ALL.asScala.reverse foreach { s =>
          printNewIssuesForMarkdown(markdown, s)
        }
        markdown.append("\n\nWatch the comments in this pull request to review them. ")
    }

    appendIssueSeverityRemark(markdown)
    markdown.toString()
  }

  private def appendIssueSeverityRemark(markdown: StringBuilder) = {
    val severityImgMarkdown = SonarUtils.toImageMarkdown(pluginConfiguration.minSeverity())
    markdown.append(
      s"""Note that only issues with severity >=
          |$severityImgMarkdown (${pluginConfiguration.minSeverity().toLowerCase})
          |are reported.""".stripMargin.replaceAll("\n", " ")
    )
  }

  def canBeApproved: Boolean =
    // there does not seam to be a way to check the quality gates in preview mode, so we make an assumption about
    // what users would consider good quality here :-)
    newIssuesBySeverity(Severity.CRITICAL) == 0 && newIssuesBySeverity(Severity.BLOCKER) == 0

  private def printNewIssuesForMarkdown(sb: StringBuilder, severity: String) = {
    val issueCount = newIssuesBySeverity(severity)
    if (issueCount > 0) {
      sb.append(s"* ${SonarUtils.toImageMarkdown(severity)} $issueCount ${severity.toLowerCase}\n")
    }
  }

}

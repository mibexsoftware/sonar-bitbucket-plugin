package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.PluginConfiguration
import ch.mibex.bitbucket.sonar.utils.{MarkdownUtils, StringUtils}
import org.sonar.api.issue.Issue
import org.sonar.api.rule.Severity

import scala.collection.JavaConverters._
import scala.collection.mutable


class PullRequestReviewResults(pluginConfiguration: PluginConfiguration) {
  private val newIssuesBySeverity = new mutable.HashMap[String, Int]().withDefaultValue(0)

  def issueFound(issue: Issue): Unit = {
    newIssuesBySeverity(issue.severity()) += 1
  }

  def formatAsMarkdown(): String = {
    val markdown = new StringBuilder()
    markdown.append(s"${MarkdownUtils.sonarPrefix()} reported ")

    newIssuesBySeverity.values.toList match {
      case Nil =>
        markdown.append("no issues. Take a chocolate :-)")
      case xs =>
        markdown.append(s"${xs.sum} ${StringUtils.pluralise("issue", xs.sum)}:\n\n")
        Severity.ALL.asScala.reverse foreach { s =>
          printNewIssuesForMarkdown(markdown, s)
        }
        markdown.append("\n\nWatch the comments in this pull request to review them.")
        val severityImgMarkdown = MarkdownUtils.getImageMarkdownForSeverity(pluginConfiguration.minSeverity())
        markdown.append(s" Note that only issues with severity >= $severityImgMarkdown are shown in this pull request.")
    }

    markdown.toString()
  }

  def canBeApproved: Boolean =
    newIssuesBySeverity(Severity.CRITICAL) == 0 && newIssuesBySeverity(Severity.BLOCKER) == 0

  private def printNewIssuesForMarkdown(sb: StringBuilder, severity: String) = {
    val issueCount = newIssuesBySeverity(severity)
    if (issueCount > 0) {
      sb.append("* ")
        .append(MarkdownUtils.getImageMarkdownForSeverity(severity))
        .append(" ")
        .append(issueCount)
        .append(" ")
        .append(severity.toLowerCase)
        .append("\n")
    }
  }

}

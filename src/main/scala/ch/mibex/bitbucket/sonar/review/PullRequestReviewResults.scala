package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import ch.mibex.bitbucket.sonar.client.{BuildStatus, FailingBuildStatus, SuccessfulBuildstatus}
import ch.mibex.bitbucket.sonar.utils.{SonarUtils, StringUtils}
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity

import scala.collection.immutable.HashMap


class PullRequestReviewResults(pluginConfiguration: SonarBBPluginConfig) {
  private var newIssuesBySeverity: Map[Severity, Int] = HashMap().withDefaultValue(0)

  def issueFound(issue: PostJobIssue): Unit = {
    newIssuesBySeverity += (issue.severity() -> (newIssuesBySeverity(issue.severity()) + 1))
  }

  def calculateBuildStatus(): BuildStatus = {
    val issuesWithAboveMaxSeverity = countIssuesWithAboveMaxSeverity
    if (issuesWithAboveMaxSeverity == 0)
      SuccessfulBuildstatus
    else
      FailingBuildStatus(Severity.valueOf(pluginConfiguration.sonarApprovalSeverityLevel()), issuesWithAboveMaxSeverity)
  }

  def formatAsMarkdown(): String = {
    val markdown = new StringBuilder()
    markdown.append(s"${SonarUtils.sonarMarkdownPrefix()} reported ")

    newIssuesBySeverity.values.toList match {
      case Nil =>
        markdown.append("no issues. Take a chocolate :-)\n\n")
      case xs =>
        markdown.append(s"${xs.sum} ${StringUtils.pluralise("issue", xs.sum)}:\n\n")
        Severity.values().reverse foreach { s =>
          printNewIssuesForMarkdown(markdown, s)
        }
        markdown.append("\n\nWatch the comments in this pull request to review them. ")
    }

    appendIssueSeverityRemark(markdown)
    markdown.toString()
  }

  private def appendIssueSeverityRemark(markdown: StringBuilder) = {
    val severityImgMarkdown = SonarUtils.toImageMarkdown(Severity.valueOf(pluginConfiguration.minSeverity()))
    markdown.append(
      s"""Note that only issues with severity >=
          |$severityImgMarkdown (${pluginConfiguration.minSeverity().toLowerCase})
          |are reported.""".stripMargin.replaceAll("\n", " ")
    )
  }

  def countIssuesWithAboveMaxSeverity: Int = {
    val level = Severity.valueOf(pluginConfiguration.sonarApprovalSeverityLevel())
    val allLevels = Severity.values()
    val maxIndexLevel = allLevels.indexOf(level)
    newIssuesBySeverity
      .filter { case(severity, numIssues) => allLevels.indexOf(severity) >= maxIndexLevel }
      .foldLeft(0)(_ + _._2)
  }

  private def printNewIssuesForMarkdown(sb: StringBuilder, severity: Severity) = {
    val issueCount = newIssuesBySeverity(severity)
    if (issueCount > 0) {
      sb.append(s"* ${SonarUtils.toImageMarkdown(severity)} $issueCount ${severity.toString.toLowerCase}\n")
    }
  }

}

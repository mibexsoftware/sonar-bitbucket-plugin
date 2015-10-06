package ch.mibex.bitbucket.sonar.diff

import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest}
import ch.mibex.bitbucket.sonar.diff.GitDiffParser.GitDiff
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.issue.Issue


@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class IssuesOnChangedLinesFilter(bitbucketClient: BitbucketClient,
                                 inputFileCache: InputFileCache) extends BatchComponent {

  def filter(pullRequest: PullRequest, newIssues: Seq[Issue]): Seq[Issue] = {
    val diffs = parsePullRequestDiff(pullRequest)
    val issuesOnChangedLines = newIssues filter { i =>
      val lineNr = Option(i.line()).flatMap(l => Option(l.toInt)).getOrElse(0)
      inputFileCache.resolveRepoRelativePath(i.componentKey()) match {
        case Some(filePath) =>
          val isIssueOnChangedLines = (diff: GitDiff) =>
            (diff.gitDiffHeader.newFile == filePath || diff.gitDiffHeader.oldFile == filePath) &&
              (diff.isNewFile || isOnChangedLine(lineNr, diff))
          diffs.exists(isIssueOnChangedLines)
        case None => false  // ignore these issues
      }
    }
    issuesOnChangedLines
  }

  private def isOnChangedLine(lineNr: Int, diff: GitDiff) =
    diff.hunks.exists(c =>
      lineNr >= c.hunkHeader.fromToRange.toLineStart
      && lineNr <= c.hunkHeader.fromToRange.toLineStart + c.hunkHeader.fromToRange.toNumLines
    )

  private def parsePullRequestDiff(pullRequest: PullRequest) = {
    val diff = bitbucketClient.getPullRequestDiff(pullRequest)
    GitDiffParser.parse(diff) match {
      case Left(parsingFailure) => throw new scala.RuntimeException(s"Failed to parse git diff $parsingFailure")
      case Right(gitDiffs) => gitDiffs
    }
  }

}

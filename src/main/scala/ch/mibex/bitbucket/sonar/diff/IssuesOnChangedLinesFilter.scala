package ch.mibex.bitbucket.sonar.diff

import ch.mibex.bitbucket.sonar.SonarBBPlugin
import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest}
import ch.mibex.bitbucket.sonar.diff.GitDiffParser.{BinaryDiff, Diff, GitDiff}
import ch.mibex.bitbucket.sonar.utils.LogUtils
import org.slf4j.LoggerFactory
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.issue.Issue


@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class IssuesOnChangedLinesFilter(bitbucketClient: BitbucketClient,
                                 inputFileCache: InputFileCache) extends BatchComponent {
  private val logger = LoggerFactory.getLogger(getClass)

  def filter(pullRequest: PullRequest, newIssues: Seq[Issue]): Seq[Issue] = {
    val pullRequestDiff = bitbucketClient.getPullRequestDiff(pullRequest)
    val diffs = parseOrFail(pullRequestDiff)
    val issuesOnChangedLines = newIssues filter { i =>
      val lineNr = Option(i.line()).flatMap(l => Option(l.toInt)).getOrElse(0)

      inputFileCache.resolveRepoRelativePath(i.componentKey()) match {
        case Some(filePath) =>
          val isIssueOnChangedLines = (diff: Diff) => diff match {
            case diff: GitDiff =>
              (diff.gitDiffHeader.newFile == filePath || diff.gitDiffHeader.oldFile == filePath) &&
                (diff.isNewFile || isOnChangedLine(lineNr, diff))
            case binary: BinaryDiff => false
          }
          diffs.exists(isIssueOnChangedLines)
        case None =>
          logger.warn(LogUtils.f(s"Could not resolve path for ${i.componentKey()}"))
          false  // ignore these issues
      }
    }

    issuesOnChangedLines
  }

  private def isOnChangedLine(lineNr: Int, diff: GitDiff) =
    diff.hunks.exists(c =>
      lineNr >= c.hunkHeader.fromToRange.toLineStart
        //@@ -12 +11,0 @@ public class App
        //   -        double d = Double.longBitsToDouble(i);  // Noncompliant
        //@@ -16 +14,0 @@ public class App
        //   -        System.out.println( "Hello World! " + d);
        //@@ -26,2 +27 @@ public class App
        //   -        int i = 100023;
        //   -        System.exit(-1);
        //   +		int i = 100023;
      && c.hunkHeader.fromToRange.toNumLines.getOrElse(1) > 0
      && lineNr <= c.hunkHeader.fromToRange.toLineStart + c.hunkHeader.fromToRange.toNumLines.getOrElse(0)
    )

  private def parseOrFail(diff: String) = GitDiffParser.parse(diff) match {
    case Left(parsingFailure) =>
      throw new RuntimeException(s"${SonarBBPlugin.PluginLogPrefix} Failed to parse diff: ${parsingFailure.reason}")
    case Right(gitDiffs) =>
      gitDiffs
  }

}

package ch.mibex.bitbucket.sonar.diff

import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest}
import ch.mibex.bitbucket.sonar.diff.GitDiffParser.{BinaryDiff, Diff, GitDiff}
import ch.mibex.bitbucket.sonar.{GitBaseDirResolver, SonarBBPlugin}
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.{ScannerSide, InstantiationStrategy}

@ScannerSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class IssuesOnChangedLinesFilter(bitbucketClient: BitbucketClient,
                                 gitBaseDirResolver: GitBaseDirResolver) {

  def filter(pullRequest: PullRequest, newIssues: Seq[PostJobIssue]): Seq[PostJobIssue] = {
    val pullRequestDiff = bitbucketClient.getPullRequestDiff(pullRequest)
    val diffs = parseOrFail(pullRequestDiff)

    val issuesOnChangedLines = newIssues filter { i =>
      val lineNr = Option(i.line()).flatMap(l => Option(l.toInt)).getOrElse(0)

      gitBaseDirResolver.getRepositoryRelativePath(i) match {
        case Some(filePath) =>
          val isIssueOnChangedLines = (diff: Diff) => diff match {
            case diff: GitDiff =>
              (diff.gitDiffHeader.newFile == filePath || diff.gitDiffHeader.oldFile == filePath) &&
                (diff.isNewFile || isOnChangedLine(lineNr, diff))
            case binary: BinaryDiff => false
          }
          diffs.exists(isIssueOnChangedLines)
        case None => false // ignore non-file issues
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
    case Right(gitDiffs) => gitDiffs
  }

}

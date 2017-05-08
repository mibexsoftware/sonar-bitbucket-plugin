package ch.mibex.bitbucket.sonar.diff

import java.nio.file.Path

import ch.mibex.bitbucket.sonar.GitBaseDirResolver
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest}
import org.junit.runner.RunWith
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.rule.RuleKey
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class IssuesOnChangedLinesFilterSpec extends Specification with Mockito {
  val bitbucketClient = mock[BitbucketClient]

  private def readFile(path: String) =
    scala.io.Source.fromInputStream(getClass.getResourceAsStream(path)).mkString

  "filter" should {

    "only yield issues on changed lines in complex diff" in new ComplexIssueContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHref = None, srcCommitHash = None, dstCommitHash = None)
      bitbucketClient.getPullRequestDiff(pullRequest) returns readFile("/diffs/5diffs-example.diff")
      val issuesNotOnChangedLines = Set(issue1, issue5, issue6, issue7)
      val expectedIssues = issues diff issuesNotOnChangedLines
      val issuesOnChangedLinesFilter = new IssuesOnChangedLinesFilter(bitbucketClient, gitBaseDirResolver)

      val onlyIssuesOnChangedLines = issuesOnChangedLinesFilter.filter(pullRequest, issues.toList)

      onlyIssuesOnChangedLines must_== expectedIssues.toList
    }

    "yield no issues when none in diff" in new NoNewIssuesContext {
      val pullRequest = PullRequest(id = 2, srcBranch = "develop", srcCommitHref = None, srcCommitHash = None, dstCommitHash = None)
      bitbucketClient.getPullRequestDiff(pullRequest) returns readFile("/diffs/2diffs-example.diff")
      val issuesOnChangedLinesFilter = new IssuesOnChangedLinesFilter(bitbucketClient, gitBaseDirResolver)

      val onlyIssuesOnChangedLines = issuesOnChangedLinesFilter.filter(pullRequest, issues.toList)

      onlyIssuesOnChangedLines must beEmpty
    }

  }

  class NoNewIssuesContext extends Scope {
    val gitBaseDirResolver = mock[GitBaseDirResolver]

    val inputFile = mock[InputFile]
    inputFile.isFile returns true
    val inputFilePath = mock[Path]
    inputFilePath.toString returns "src/main/java/com/company/sonar/bitbucket/SimpleClass.java"
    inputFile.path() returns inputFilePath

    val issue1 = mock[PostJobIssue]
    issue1.severity() returns Severity.MINOR
    issue1.message() returns "Replace all tab characters in this file by sequences of white-spaces."
    issue1.line() returns null
    issue1.ruleKey() returns RuleKey.parse("squid:S00105")
    issue1.componentKey() returns "com.company:sonar-bitbucket-test:src/main/java/com/company/sonar/bitbucket/SimpleClass.java"
    issue1.inputComponent() returns inputFile
    gitBaseDirResolver.getRepositoryRelativePath(issue1) returns Option("src/main/java/com/company/sonar/bitbucket/SimpleClass.java")

    val issue2 = mock[PostJobIssue]
    issue2.severity() returns Severity.MAJOR
    issue2.message() returns """Move the "" string literal on the left side of this string comparison."""
    issue2.line() returns 5
    issue2.ruleKey() returns RuleKey.parse("squid:S1132")
    issue2.componentKey() returns "com.company:sonar-bitbucket-test:src/main/java/com/company/sonar/bitbucket/SimpleClass.java"
    issue2.inputComponent() returns inputFile
    gitBaseDirResolver.getRepositoryRelativePath(issue2) returns Option("src/main/java/com/company/sonar/bitbucket/SimpleClass.java")

    val issue3 = mock[PostJobIssue]
    issue3.severity() returns Severity.MAJOR
    issue3.message() returns """Introduce a new variable instead of reusing the parameter "bar"."""
    issue3.line() returns 6
    issue3.ruleKey() returns RuleKey.parse("squid:S1226")
    issue3.componentKey() returns "com.company:sonar-bitbucket-test:src/main/java/com/company/sonar/bitbucket/SimpleClass.java"
    issue3.inputComponent() returns inputFile
    gitBaseDirResolver.getRepositoryRelativePath(issue3) returns Option("src/main/java/com/company/sonar/bitbucket/SimpleClass.java")

    val issues = Set(issue1, issue2, issue3)
  }

  class ComplexIssueContext extends Scope {
    val gitBaseDirResolver = mock[GitBaseDirResolver]

    val appFile = mock[InputFile]
    appFile.isFile returns true
    val appFilePath = mock[Path]
    appFilePath.toString returns "multimod/src/db/src/main/java/ch/mycompany/test/db/App.java"
    appFile.path() returns appFilePath

    val issue1 = mock[PostJobIssue]
    issue1.severity() returns Severity.MAJOR
    issue1.message() returns "Either log or rethrow this exception."
    issue1.line() returns 14
    issue1.ruleKey() returns RuleKey.parse("squid:S1166")
    issue1.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue1.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue1) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue2 = mock[PostJobIssue]
    issue2.severity() returns Severity.BLOCKER
    issue2.message() returns """Remove this "Double.longBitsToDouble" call."""
    issue2.line() returns 12
    issue2.ruleKey() returns RuleKey.parse("squid:S2127")
    issue2.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue2.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue2) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue3 = mock[PostJobIssue]
    issue3.severity() returns Severity.MAJOR
    issue3.message() returns "Replace this usage of System.out or System.err by a logger."
    issue3.line() returns 16
    issue3.ruleKey() returns RuleKey.parse("squid:S106")
    issue3.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue3.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue3) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue4 = mock[PostJobIssue]
    issue4.severity() returns Severity.BLOCKER
    issue4.message() returns """Catch Exception instead of Throwable."""
    issue4.line() returns 23
    issue4.ruleKey() returns RuleKey.parse("squid:S1181")
    issue4.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue4.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue4) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue5 = mock[PostJobIssue]
    issue5.severity() returns Severity.MAJOR
    issue5.message() returns """Either remove or fill this block of code."""
    issue5.line() returns 13
    issue5.ruleKey() returns RuleKey.parse("squid:S00108")
    issue5.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue5.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue5) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue6 = mock[PostJobIssue]
    issue6.severity() returns Severity.MAJOR
    issue6.message() returns """Either remove or fill this block of code."""
    issue6.line() returns 14
    issue6.ruleKey() returns RuleKey.parse("squid:S00108")
    issue6.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue6.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue6) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue7 = mock[PostJobIssue]
    issue7.severity() returns Severity.MAJOR
    issue7.message() returns """Either remove or fill this block of code."""
    issue7.line() returns 14
    issue7.ruleKey() returns RuleKey.parse("squid:S00108")
    issue7.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue7.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue7) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue8 = mock[PostJobIssue]
    issue8.severity() returns Severity.MAJOR
    issue8.message() returns """Either log or rethrow this exception."""
    issue8.line() returns 23
    issue8.ruleKey() returns RuleKey.parse("squid:S1166")
    issue8.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue8.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue8) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue9 = mock[PostJobIssue]
    issue9.severity() returns Severity.MAJOR
    issue9.message() returns """Either remove or fill this block of code."""
    issue9.line() returns 23
    issue9.ruleKey() returns RuleKey.parse("squid:S00108")
    issue9.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue9.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue9) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue10 = mock[PostJobIssue]
    issue10.severity() returns Severity.MAJOR
    issue10.message() returns """Either remove or fill this block of code."""
    issue10.line() returns 22
    issue10.ruleKey() returns RuleKey.parse("squid:S00108")
    issue10.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue10.inputComponent() returns appFile
    gitBaseDirResolver.getRepositoryRelativePath(issue10) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue11 = mock[PostJobIssue]
    issue11.severity() returns Severity.MINOR
    issue11.message() returns """Replace all tab characters in this file by sequences of white-spaces."""
    issue11.line() returns null
    issue11.ruleKey() returns RuleKey.parse("squid:S00105")
    issue11.componentKey() returns "ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java"
    val stashTagFile = mock[InputFile]
    val stashTagFilePath = mock[Path]
    stashTagFilePath.toString returns "multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java"
    stashTagFile.isFile returns true
    stashTagFile.path() returns stashTagFilePath
    issue11.inputComponent() returns stashTagFile
    gitBaseDirResolver.getRepositoryRelativePath(issue11) returns Option("multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java")

    val issues = Set(issue1, issue2, issue3, issue4, issue5, issue6, issue7, issue8, issue9, issue10, issue11)
  }

}
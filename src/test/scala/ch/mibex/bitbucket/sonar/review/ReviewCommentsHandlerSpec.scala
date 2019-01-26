package ch.mibex.bitbucket.sonar.review

import java.nio.file.Path

import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.{GitBaseDirResolver, SonarBBPlugin, SonarBBPluginConfig}
import org.junit.runner.RunWith
import org.sonar.api.CoreProperties
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.config.internal.MapSettings
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.platform.Server
import org.sonar.api.rule.RuleKey
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class ReviewCommentsHandlerSpec extends Specification with Mockito {

  "createOrUpdateComments" should {

    "do not create new comments for already existing equivalent comments" in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHref = None, srcCommitHash = Some("0affee"), dstCommitHash = Some("0affee"))
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[PostJobIssue]]) returns issues
      val existingReviewComments = List(PullRequestComment(
        commentId = 1,
        content =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) **BLOCKER**: Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")))
      reviewCommentsCreator.updateComments(pullRequest, issues, existingReviewComments, pullRequestResults)

      there was no(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) **BLOCKER**: Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some(
          "multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )
    }

    "update comment if one already exists on the same file and line " in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHref = None, srcCommitHash = Some("0affee"), dstCommitHash = Some("0affee"))
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[PostJobIssue]]) returns issues
      val existingReviewComments = List(PullRequestComment(
        commentId = 1,
        content =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")))
      reviewCommentsCreator.updateComments(pullRequest, issues, existingReviewComments, pullRequestResults)

      there was one(bitbucketClient).updateReviewComment(
        pullRequest,
        commentId = 1,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) **BLOCKER**: Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin
      )
    }

    "create comments for all issues found when there are no existing comments" in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHref = None, srcCommitHash =  Some("0affee"), dstCommitHash =  Some("0affee"))
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[PostJobIssue]]) returns issues
      reviewCommentsCreator.updateComments(pullRequest, issues, existingReviewComments = List(), pullRequestResults)

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) **BLOCKER**: Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(14),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(22),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )


      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(13),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) **MAJOR**: Replace this usage of System.out or System.err by a logger. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS106)""".stripMargin,
        line = Some(16),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) **BLOCKER**: Remove this "Double.longBitsToDouble" call. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS2127)""".stripMargin,
        line = Some(12),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

//      there was one(bitbucketClient).createPullRequestComment(
//        pullRequest,
//        message =
//          """**SonarQube Analysis**
//            |
//            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
//        line = Some(0),
//        filePath = Some("multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java")
//      )

    }

  }

  class ReviewContext extends Scope {
    val settings = new MapSettings(new PropertyDefinitions(classOf[SonarBBPlugin]))
    val server = mock[Server]
    val pluginConfig = new SonarBBPluginConfig(settings, server)
    val bitbucketClient = mock[BitbucketClient]
    val sonarSettings = mock[Settings]
    sonarSettings.hasKey(CoreProperties.SERVER_BASE_URL) returns true
    sonarSettings.getString(CoreProperties.SERVER_BASE_URL) returns "http://localhost:9000"
    val issuesOnChangedLinesFilter = mock[IssuesOnChangedLinesFilter]
    val gitBaseDirResolver = mock[GitBaseDirResolver]
    val reviewCommentsCreator = new ReviewCommentsHandler(
      bitbucketClient, pluginConfig, sonarSettings, gitBaseDirResolver, issuesOnChangedLinesFilter
    )
    val pullRequestResults = new PullRequestReviewResults(pluginConfig)
    val appfile = mock[InputFile]
    appfile.isFile returns true
    val appfilePath = mock[Path]
    appfilePath.toString returns "multimod/src/db/src/main/java/ch/mycompany/test/db/App.java"
    appfile.path() returns appfilePath
    val stashTagFile = mock[InputFile]
    stashTagFile.isFile returns true
    val stashTagFilePath = mock[Path]
    stashTagFilePath.toString returns "multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java"
    stashTagFile.path() returns stashTagFilePath

    val issue1 = mock[PostJobIssue]
    issue1.severity() returns Severity.MAJOR
    issue1.message() returns "Either log or rethrow this exception."
    issue1.line() returns 14
    issue1.ruleKey() returns RuleKey.parse("squid:S1166")
    issue1.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue1.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue1) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue2 = mock[PostJobIssue]
    issue2.severity() returns Severity.BLOCKER
    issue2.message() returns """Remove this "Double.longBitsToDouble" call."""
    issue2.line() returns 12
    issue2.ruleKey() returns RuleKey.parse("squid:S2127")
    issue2.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue2.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue2) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue3 = mock[PostJobIssue]
    issue3.severity() returns Severity.MAJOR
    issue3.message() returns "Replace this usage of System.out or System.err by a logger."
    issue3.line() returns 16
    issue3.ruleKey() returns RuleKey.parse("squid:S106")
    issue3.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue3.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue3) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue4 = mock[PostJobIssue]
    issue4.severity() returns Severity.BLOCKER
    issue4.message() returns """Catch Exception instead of Throwable."""
    issue4.line() returns 23
    issue4.ruleKey() returns RuleKey.parse("squid:S1181")
    issue4.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue4.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue4) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue5 = mock[PostJobIssue]
    issue5.severity() returns Severity.MAJOR
    issue5.message() returns """Either remove or fill this block of code."""
    issue5.line() returns 13
    issue5.ruleKey() returns RuleKey.parse("squid:S00108")
    issue5.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue5.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue5) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue6 = mock[PostJobIssue]
    issue6.severity() returns Severity.MAJOR
    issue6.message() returns """Either remove or fill this block of code."""
    issue6.line() returns 14
    issue6.ruleKey() returns RuleKey.parse("squid:S00108")
    issue6.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue6.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue6) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue7 = mock[PostJobIssue]
    issue7.severity() returns Severity.MAJOR
    issue7.message() returns """Either remove or fill this block of code."""
    issue7.line() returns 14
    issue7.ruleKey() returns RuleKey.parse("squid:S00108")
    issue7.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue7.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue7) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue8 = mock[PostJobIssue]
    issue8.severity() returns Severity.MAJOR
    issue8.message() returns """Either log or rethrow this exception."""
    issue8.line() returns 23
    issue8.ruleKey() returns RuleKey.parse("squid:S1166")
    issue8.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue8.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue8) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue9 = mock[PostJobIssue]
    issue9.severity() returns Severity.MAJOR
    issue9.message() returns """Either remove or fill this block of code."""
    issue9.line() returns 23
    issue9.ruleKey() returns RuleKey.parse("squid:S00108")
    issue9.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue9.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue9) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue10 = mock[PostJobIssue]
    issue10.severity() returns Severity.MAJOR
    issue10.message() returns """Either remove or fill this block of code."""
    issue10.line() returns 22
    issue10.ruleKey() returns RuleKey.parse("squid:S00108")
    issue10.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"
    issue10.inputComponent() returns appfile
    gitBaseDirResolver.getRepositoryRelativePath(issue10) returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")

    val issue11 = mock[PostJobIssue]
    issue11.severity() returns Severity.MINOR
    issue11.message() returns """Replace all tab characters in this file by sequences of white-spaces."""
    issue11.line() returns null
    issue11.ruleKey() returns RuleKey.parse("squid:S00105")
    issue11.componentKey() returns "ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java"
    issue11.inputComponent() returns stashTagFile
    gitBaseDirResolver.getRepositoryRelativePath(issue11) returns Option("ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java")

    val issues = List(issue1, issue2, issue3, issue4, issue5, issue6, issue7, issue8, issue9, issue10, issue11)
  }

}
package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest, PullRequestComment}
import ch.mibex.bitbucket.sonar.diff.IssuesOnChangedLinesFilter
import ch.mibex.bitbucket.sonar.{SonarBBPlugin, SonarBBPluginConfig, SonarBBPlugin$}
import org.junit.runner.RunWith
import org.sonar.api.CoreProperties
import org.sonar.api.config.{PropertyDefinitions, Settings}
import org.sonar.api.issue.{Issue, ProjectIssues}
import org.sonar.api.platform.Server
import org.sonar.api.rule.{RuleKey, Severity}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ReviewCommentsUpdaterSpec extends Specification with Mockito {

  "createOrUpdateComments" should {

    "do not create new comments for already existing equivalent comments" in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHash = "0affee", dstCommitHash = "0affee")
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[Issue]]) returns issues
      val existingReviewComments = List(PullRequestComment(
        commentId = 1,
        content =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")))
      reviewCommentsCreator.createOrUpdateComments(pullRequest, existingReviewComments, pullRequestResults)

      there was no(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some(
          "multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )
    }

    "update comment if one already exists on the same file and line " in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHash = "0affee", dstCommitHash = "0affee")
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[Issue]]) returns issues
      val existingReviewComments = List(PullRequestComment(
        commentId = 1,
        content =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")))
      reviewCommentsCreator.createOrUpdateComments(pullRequest, existingReviewComments, pullRequestResults)

      there was one(bitbucketClient).updateReviewComment(
        pullRequest,
        commentId = 1,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin
      )
    }

    "create comments for all issues found when there are no existing comments" in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHash = "0affee", dstCommitHash = "0affee")
      issuesOnChangedLinesFilter.filter(any[PullRequest], any[Seq[Issue]]) returns issues
      reviewCommentsCreator.createOrUpdateComments(pullRequest, existingReviewComments = List(), pullRequestResults)

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) Catch Exception instead of Throwable. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1181)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(23),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either log or rethrow this exception. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS1166)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(14),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(22),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )


      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Either remove or fill this block of code. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS00108)""".stripMargin,
        line = Some(13),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![MAJOR](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/MAJOR.png) Replace this usage of System.out or System.err by a logger. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS106)""".stripMargin,
        line = Some(16),
        filePath = Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      )

      there was one(bitbucketClient).createPullRequestComment(
        pullRequest,
        message =
          """**SonarQube Analysis**
            |
            |![BLOCKER](https://raw.githubusercontent.com/mibexsoftware/sonar-bitbucket-plugin/master/src/main/resources/images/severity/BLOCKER.png) Remove this "Double.longBitsToDouble" call. [[Details]](http://localhost:9000/coding_rules#rule_key=squid%3AS2127)""".stripMargin,
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
    val settings = new Settings(new PropertyDefinitions(classOf[SonarBBPlugin]))
    val server = mock[Server]
    val pluginConfig = new SonarBBPluginConfig(settings, server)
    val projectIssues = mock[ProjectIssues]
    val bitbucketClient = mock[BitbucketClient]
    val sonarSettings = mock[Settings]
    sonarSettings.hasKey(CoreProperties.SERVER_BASE_URL) returns true
    sonarSettings.getString(CoreProperties.SERVER_BASE_URL) returns "http://localhost:9000"
    val issuesOnChangedLinesFilter = mock[IssuesOnChangedLinesFilter]
    val inputFileCache = mock[InputFileCache]
    val reviewCommentsCreator = new ReviewCommentsCreator(
      projectIssues, bitbucketClient, inputFileCache, pluginConfig, sonarSettings, issuesOnChangedLinesFilter
    )
    val pullRequestResults = new PullRequestReviewResults(pluginConfig)
    (inputFileCache.resolveRepoRelativePath("ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java")
      returns Some("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java"))
    (inputFileCache.resolveRepoRelativePath("ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java")
      returns Some("multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java"))

    val issue1 = mock[Issue]
    issue1.severity() returns Severity.MAJOR
    issue1.message() returns "Either log or rethrow this exception."
    issue1.line() returns 14
    issue1.ruleKey() returns RuleKey.parse("squid:S1166")
    issue1.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue2 = mock[Issue]
    issue2.severity() returns Severity.BLOCKER
    issue2.message() returns """Remove this "Double.longBitsToDouble" call."""
    issue2.line() returns 12
    issue2.ruleKey() returns RuleKey.parse("squid:S2127")
    issue2.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue3 = mock[Issue]
    issue3.severity() returns Severity.MAJOR
    issue3.message() returns "Replace this usage of System.out or System.err by a logger."
    issue3.line() returns 16
    issue3.ruleKey() returns RuleKey.parse("squid:S106")
    issue3.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue4 = mock[Issue]
    issue4.severity() returns Severity.BLOCKER
    issue4.message() returns """Catch Exception instead of Throwable."""
    issue4.line() returns 23
    issue4.ruleKey() returns RuleKey.parse("squid:S1181")
    issue4.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue5 = mock[Issue]
    issue5.severity() returns Severity.MAJOR
    issue5.message() returns """Either remove or fill this block of code."""
    issue5.line() returns 13
    issue5.ruleKey() returns RuleKey.parse("squid:S00108")
    issue5.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue6 = mock[Issue]
    issue6.severity() returns Severity.MAJOR
    issue6.message() returns """Either remove or fill this block of code."""
    issue6.line() returns 14
    issue6.ruleKey() returns RuleKey.parse("squid:S00108")
    issue6.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue7 = mock[Issue]
    issue7.severity() returns Severity.MAJOR
    issue7.message() returns """Either remove or fill this block of code."""
    issue7.line() returns 14
    issue7.ruleKey() returns RuleKey.parse("squid:S00108")
    issue7.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue8 = mock[Issue]
    issue8.severity() returns Severity.MAJOR
    issue8.message() returns """Either log or rethrow this exception."""
    issue8.line() returns 23
    issue8.ruleKey() returns RuleKey.parse("squid:S1166")
    issue8.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue9 = mock[Issue]
    issue9.severity() returns Severity.MAJOR
    issue9.message() returns """Either remove or fill this block of code."""
    issue9.line() returns 23
    issue9.ruleKey() returns RuleKey.parse("squid:S00108")
    issue9.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue10 = mock[Issue]
    issue10.severity() returns Severity.MAJOR
    issue10.message() returns """Either remove or fill this block of code."""
    issue10.line() returns 22
    issue10.ruleKey() returns RuleKey.parse("squid:S00108")
    issue10.componentKey() returns "ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java"

    val issue11 = mock[Issue]
    issue11.severity() returns Severity.MINOR
    issue11.message() returns """Replace all tab characters in this file by sequences of white-spaces."""
    issue11.line() returns null
    issue11.ruleKey() returns RuleKey.parse("squid:S00105")
    issue11.componentKey() returns "ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java"

    val issues = List(issue1, issue2, issue3, issue4, issue5, issue6, issue7, issue8, issue9, issue10, issue11)
    projectIssues.issues() returns issues.asJava
  }

}
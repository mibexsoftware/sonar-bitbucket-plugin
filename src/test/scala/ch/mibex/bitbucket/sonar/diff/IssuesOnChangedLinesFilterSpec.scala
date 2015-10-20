package ch.mibex.bitbucket.sonar.diff

import ch.mibex.bitbucket.sonar.cache.InputFileCache
import ch.mibex.bitbucket.sonar.client.{BitbucketClient, PullRequest}
import org.junit.runner.RunWith
import org.sonar.api.issue.Issue
import org.sonar.api.rule.{RuleKey, Severity}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class IssuesOnChangedLinesFilterSpec extends Specification with Mockito {

  "filter" should {

    "only yield issues on changed lines" in new ReviewContext {
      val pullRequest = PullRequest(id = 1, srcBranch = "develop", srcCommitHash = "", dstCommitHash = "")
      val inputFileCache = mock[InputFileCache]
      (inputFileCache.resolveRepoRelativePath("ch.mycompany.test:db:src/main/java/ch/mycompany/test/db/App.java")
        returns Option("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java"))
      (inputFileCache.resolveRepoRelativePath("ch.mycompany.test:gui:src/main/java/ch/mycompany/test/gui/StashTag.java")
        returns Option("multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java"))
      bitbucketClient.getPullRequestDiff(pullRequest) returns diff

      val issuesOnChangedLinesFilter = new IssuesOnChangedLinesFilter(bitbucketClient, inputFileCache)

      val issuesNotOnChangedLines = Set(issue1, issue5, issue6, issue7)
      val expectedIssues = issues diff issuesNotOnChangedLines
      issuesOnChangedLinesFilter.filter(pullRequest, issues.toList) must_== expectedIssues.toList
    }

  }

  class ReviewContext extends Scope {
    val bitbucketClient = mock[BitbucketClient]

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

    val issues = Set(issue1, issue2, issue3, issue4, issue5, issue6, issue7, issue8, issue9, issue10, issue11)
  }

  val diff =
    """diff --git a/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java b/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java
      |index 57b70a8..d6e8952 100755
      |--- a/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java
      |+++ b/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java
      |@@ -12 +12 @@ public class App
      |-        double d = Double.longBitsToDouble(i);  // Noncompliant
      |+        double g = Double.longBitsToDouble(i);  // Noncompliant
      |@@ -16 +16 @@ public class App
      |-        System.out.println( "Hello World! " + d);
      |+        System.out.println( "Hello World! " + g);
      |@@ -17,0 +18,10 @@ public class App
      |+
      |+	public void foo () {
      |+		int f = 42;
      |+
      |+        try {
      |+        } catch (Throwable t) {
      |+        }
      |+	}
      |+
      |+	private void foo3() { }
      |diff --git a/multimod/src/db/src/test/java/ch/mycompany/test/db/AppTest.java b/multimod/src/db/src/test/java/ch/mycompany/test/db/AppTest.java
      |deleted file mode 100755
      |index 4545ecc..0000000
      |--- a/multimod/src/db/src/test/java/ch/mycompany/test/db/AppTest.java
      |+++ /dev/null
      |@@ -1,40 +0,0 @@
      |-package ch.mycompany.test.db;
      |-
      |-import junit.framework.Test;
      |-import junit.framework.TestCase;
      |-import junit.framework.TestSuite;
      |-
      |-
      |-/**
      |- * Unit test for simple App.
      |- */
      |-public class AppTest
      |-    extends TestCase
      |-{
      |-    /**
      |-     * Create the test case
      |-     *
      |-     * @param testName name of the test case
      |-     */
      |-    public AppTest( String testName )
      |-    {
      |-        super( testName );
      |-    }
      |-
      |-    /**
      |-     * @return the suite of tests being tested
      |-     */
      |-    public static Test suite()
      |-    {
      |-        return new TestSuite( AppTest.class );
      |-    }
      |-
      |-    /**
      |-     * Rigourous Test :-)
      |-     */
      |-    public void testApp()
      |-    {
      |-        ch.mycompany.test.db.App.main(new String[]{});
      |-        assertTrue( true );
      |-    }
      |-}
      |diff --git a/multimod/src/gui/src/main/java/ch/mycompany/test/gui/App.java b/multimod/src/gui/src/main/java/ch/mycompany/test/gui/App.java
      |index 356f4b7..6ca26ea 100755
      |--- a/multimod/src/gui/src/main/java/ch/mycompany/test/gui/App.java
      |+++ b/multimod/src/gui/src/main/java/ch/mycompany/test/gui/App.java
      |@@ -8,0 +9 @@ public class App
      |+        double d = Double.longBitsToDouble(42);  // Noncompliant
      |@@ -26,2 +27,2 @@ public class App
      |-        int i = 100023;
      |-        System.exit(-1);
      |+        int i = 100024;
      |+         System.exit(-1);
      |@@ -40,0 +42,7 @@ public class App
      |+
      |+    private void foo2() {
      |+        int baba = 5555;
      |+        //TODO fix this
      |+    }
      |+
      |+
      |diff --git a/multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java b/multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java
      |new file mode 100755
      |index 0000000..3052a0b
      |--- /dev/null
      |+++ b/multimod/src/gui/src/main/java/ch/mycompany/test/gui/StashTag.java
      |@@ -0,0 +1,26 @@
      |+package ch.mycompany.test.gui;
      |+
      |+public class StashTag {
      |+    private final String id;
      |+    private final String displayId;
      |+
      |+    public StashTag(String id, String displayId) {
      |+   	//FIXME
      |+        id = id;
      |+        this.id = id;
      |+        this.displayId = displayId;
      |+    }
      |+
      |+    // value is being used by Velocity template
      |+    @SuppressWarnings("UnusedDeclaration")
      |+    public String getId() {
      |+        return id;
      |+    }
      |+
      |+    // value is being used by Velocity template
      |+    @SuppressWarnings("UnusedDeclaration")
      |+    public String getDisplayId() {
      |+	String s = null;
      |+        return displayId;
      |+    }
      |+}
      |diff --git a/sonar.json b/sonar.json
      |index 69165e1..b8fc582 100644
      |--- a/sonar.json
      |+++ b/sonar.json
      |@@ -0,0 +1 @@
      |+<<<<<<< destination:d657f52f8f651d212c3399785bff035ae29bdd99
      |@@ -3,0 +5,5 @@
      |+=======
      |+{
      |+	'sonarHost': 'https://myhost.com',
      |+	'sonarProjectKey': 'ch.mycompany.test:multimod:develop'
      |+>>>>>>> source:c778919ef079ad35688c7f5dc2ecb42aee57ae56
      |""".stripMargin

}
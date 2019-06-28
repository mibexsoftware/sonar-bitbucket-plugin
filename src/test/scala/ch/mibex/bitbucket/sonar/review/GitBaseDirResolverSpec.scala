package ch.mibex.bitbucket.sonar.review

import java.io.File
import java.nio.file.Path

import ch.mibex.bitbucket.sonar.{GitBaseDirResolver, SonarBBPluginConfig}
import org.junit.runner.RunWith
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.rule.Severity
import org.sonar.api.rule.RuleKey
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GitBaseDirResolverSpec extends Specification with Mockito {

  "getRepositoryRelativePath" should {

    "resolve Git relative path of given file" in {
      val resolver = new GitBaseDirResolver(mock[SonarBBPluginConfig])
      val baseDir = getClass.getResource("/gitrepo/multimod/src")
      resolver.init(new File(baseDir.getPath), "git")

      val inputFile = mock[InputFile]
      inputFile.isFile returns true
      val filePath = getClass.getResource("/gitrepo/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java").getPath
      inputFile.file() returns new File(filePath)
      val issue = mock[PostJobIssue]
      issue.inputComponent() returns inputFile

      resolver.getRepositoryRelativePath(issue) must
        beSome("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
    }

  }

}
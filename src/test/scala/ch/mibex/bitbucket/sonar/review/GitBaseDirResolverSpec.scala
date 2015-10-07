package ch.mibex.bitbucket.sonar.review

import java.io.File

import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GitBaseDirResolverSpec extends Specification with Mockito {

  "getRepositoryRelativePath" should {

    "resolve Git relative path of given file" in {
      val resolver = new GitBaseDirResolver
      val file = getClass.getResource("/gitrepo/multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
      val baseDir = getClass.getResource("/gitrepo/multimod/src")
      resolver.init(new File(baseDir.getPath), "git")
      resolver.getRepositoryRelativePath(new File(file.getPath)) must
        beSome("multimod/src/db/src/main/java/ch/mycompany/test/db/App.java")
    }

  }

}
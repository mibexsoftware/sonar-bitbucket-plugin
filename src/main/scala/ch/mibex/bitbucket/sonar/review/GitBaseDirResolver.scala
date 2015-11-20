package ch.mibex.bitbucket.sonar.review

import java.io.File

import ch.mibex.bitbucket.sonar.SonarBBPlugin
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.scan.filesystem.PathResolver

import scala.annotation.tailrec

// this class is necessary because Sonar's InputFile#relativePath does not work if the working directory of the Sonar
// build is not the same as the repository root; in this case, we have to go up in the directory until we find
// the git root (.git folder)
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class GitBaseDirResolver extends BatchComponent {
  private var gitBaseDir: File = _

  def init(startDir: File, gitDirName: String = ".git"): Unit = {
    val gitBaseDir = findRepositoryBaseDir(startDir, gitDirName)
    require(
      Option(gitBaseDir).nonEmpty,
      s"${SonarBBPlugin.PluginLogPrefix} Unable to locate Git base directory in ${startDir.getAbsolutePath}"
    )
  }

  def getRepositoryRelativePath(inputFile: File): Option[String] =
    Option(new PathResolver().relativePath(gitBaseDir, inputFile))

  @tailrec
  private def findRepositoryBaseDir(baseDir: File, gitDirName: String): Option[File] = Option(baseDir) match {
    case Some(_) =>
      if (new File(baseDir, gitDirName).exists()) {
        gitBaseDir = baseDir
        return Option(baseDir)
      }
      findRepositoryBaseDir(baseDir.getParentFile, gitDirName)
    case None => None
  }

}

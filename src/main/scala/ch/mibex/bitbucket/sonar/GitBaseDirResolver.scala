package ch.mibex.bitbucket.sonar

import java.io.File

import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.postjob.issue.PostJobIssue
import org.sonar.api.batch.{ScannerSide, InstantiationStrategy}
import org.sonar.api.scan.filesystem.PathResolver

import scala.annotation.tailrec

// this class is necessary because Sonar's InputFile#relativePath does not work if the working directory of the Sonar
// build is not the same as the repository root; in this case, we have to go up in the directory until we find
// the git root (.git folder)
@ScannerSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class GitBaseDirResolver {
  private var gitBaseDir: File = _

  def init(startDir: File, gitDirName: String = ".git"): Unit = {
    gitBaseDir = findRepositoryBaseDir(Option(startDir), gitDirName).getOrElse(
      throw new IllegalArgumentException(
        s"${SonarBBPlugin.PluginLogPrefix} Unable to locate Git directory in ${startDir.getAbsolutePath}"
      )
    )
  }

  def getRepositoryRelativePath(issue: PostJobIssue): Option[String] = {
    require(Option(gitBaseDir).isDefined, "Git base directory must be set")
    if (issue.inputComponent().isFile) {
      val inputFile = issue.inputComponent().asInstanceOf[InputFile]
      Option(new PathResolver().relativePath(gitBaseDir, inputFile.file()))
    } else None
  }

  @tailrec
  private def findRepositoryBaseDir(optBaseDir: Option[File], gitDirName: String): Option[File] = optBaseDir match {
    case Some(baseDir) =>
      if (new File(baseDir, gitDirName).exists()) optBaseDir
      else findRepositoryBaseDir(Option(baseDir.getParentFile), gitDirName)
    case None => None
  }

}

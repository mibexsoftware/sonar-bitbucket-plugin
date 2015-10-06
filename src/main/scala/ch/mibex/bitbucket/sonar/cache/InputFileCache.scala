package ch.mibex.bitbucket.sonar.cache

import ch.mibex.bitbucket.sonar.GitBaseDirResolver
import org.sonar.api.BatchComponent
import org.sonar.api.batch.InstantiationStrategy
import org.sonar.api.batch.fs.InputFile

import scala.collection.mutable

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
class InputFileCache(gitBaseDirResolver: GitBaseDirResolver) extends BatchComponent {
  private val componentKeysByFile = new mutable.HashMap[String, InputFile]()
  
  def put(componentKey: String, inputFile: InputFile): Unit = {
    componentKeysByFile(componentKey) = inputFile
  }
  
  def resolveRepoRelativePath(componentKey: String): Option[String] =
    componentKeysByFile.get(componentKey) flatMap { f =>
      gitBaseDirResolver.getRepositoryRelativePath(f.file())
    }

}
package ch.mibex.bitbucket.sonar.cache

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import org.sonar.api.batch.fs.FileSystem
import org.sonar.api.batch.{Sensor, SensorContext}
import org.sonar.api.resources.Project

import scala.collection.JavaConverters._

class InputFileCacheSensor(pluginConfig: SonarBBPluginConfig,
                           fileSystem: FileSystem,
                           inputFileCache: InputFileCache) extends Sensor {
  
  override def shouldExecuteOnProject(project: Project): Boolean = pluginConfig.validate()

  override def analyse(project: Project, context: SensorContext): Unit = {
    fileSystem.inputFiles(fileSystem.predicates().all()).asScala foreach { inputFile =>
      val componentKey = context.getResource(inputFile).getEffectiveKey
      inputFileCache.put(componentKey, inputFile)
    }
  }
  
}
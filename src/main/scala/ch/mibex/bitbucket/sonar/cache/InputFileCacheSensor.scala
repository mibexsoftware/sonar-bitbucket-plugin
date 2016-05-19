package ch.mibex.bitbucket.sonar.cache

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import ch.mibex.bitbucket.sonar.utils.LogUtils
import org.slf4j.LoggerFactory
import org.sonar.api.batch.fs.FileSystem
import org.sonar.api.batch.{Sensor, SensorContext}
import org.sonar.api.resources.Project

import scala.collection.JavaConverters._

class InputFileCacheSensor(pluginConfig: SonarBBPluginConfig,
                           fileSystem: FileSystem,
                           inputFileCache: InputFileCache) extends Sensor {
  private val logger = LoggerFactory.getLogger(getClass)

  override def shouldExecuteOnProject(project: Project): Boolean = pluginConfig.validate()

  override def analyse(project: Project, context: SensorContext): Unit = {
    logger.debug(LogUtils.f("Going to fetch mappings between Sonar component keys and file paths..."))
    fileSystem.inputFiles(fileSystem.predicates().all()).asScala foreach { inputFile =>
      val componentKey = context.getResource(inputFile).getEffectiveKey
      if (logger.isDebugEnabled) {
        logger.debug(LogUtils.f(s"Found mapping $componentKey -> ${inputFile.relativePath()}"))
      }
      inputFileCache.put(componentKey, inputFile)
    }
  }
  
}
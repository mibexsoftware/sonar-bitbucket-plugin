package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.SonarBBPluginConfig
import org.sonar.api.batch.bootstrap.ProjectBuilder
import org.sonar.api.config.Settings

// this builder is executed at the beginning of the SonarQube analysis
class PullRequestProjectBuilder(pluginConfiguration: SonarBBPluginConfig,
                                gitBaseDirResolver: GitBaseDirResolver,
                                settings: Settings) extends ProjectBuilder {

  override def build(context: ProjectBuilder.Context): Unit = {
    if (pluginConfiguration.isEnabled) {
      gitBaseDirResolver.init(context.projectReactor().getRoot.getBaseDir)
    }
  }

}

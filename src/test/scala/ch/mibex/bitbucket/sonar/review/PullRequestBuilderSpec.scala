package ch.mibex.bitbucket.sonar.review

import ch.mibex.bitbucket.sonar.{GitBaseDirResolver, SonarBBPluginConfig}
import org.junit.runner.RunWith
import org.sonar.api.CoreProperties
import org.sonar.api.batch.bootstrap.ProjectBuilder
import org.sonar.api.config.Settings
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.withSettings

@RunWith(classOf[JUnitRunner])
class PullRequestBuilderSpec extends Specification with Mockito {

  class ProjectBuilderContext extends Scope {
    val pluginConfiguration = mock[SonarBBPluginConfig]
    pluginConfiguration.isEnabled returns true
    val gitBaseDirResolver = mock[GitBaseDirResolver]
    val projectContext = mock[ProjectBuilder.Context](withSettings.defaultAnswer(RETURNS_DEEP_STUBS.get))
    projectContext.projectReactor().getRoot.getBaseDir returns null
    val settings = mock[Settings]

    val projectBuilder = new PullRequestProjectBuilder(pluginConfiguration, gitBaseDirResolver, settings)
  }

  "project builder" should {

    "fail when standard analysis is used" in new ProjectBuilderContext {
      settings.getString(CoreProperties.ANALYSIS_MODE) returns CoreProperties.ANALYSIS_MODE_ANALYSIS
      projectBuilder.build(projectContext) must throwA(
        new IllegalArgumentException(
          """requirement failed: [sonar4bitbucket] The plug-in only works in preview or issues mode.
            |Please set "-Dsonar.analysis.mode" accordingly.""".stripMargin.replace("\n", " ")
        )
      )
    }

    "allow analysis when preview mode is used" in new ProjectBuilderContext {
      settings.getString(CoreProperties.ANALYSIS_MODE) returns CoreProperties.ANALYSIS_MODE_PREVIEW
      projectBuilder.build(projectContext)
    }

    "allow analysis when SonarQube 5.2's issues mode is used" in new ProjectBuilderContext {
      settings.getString(CoreProperties.ANALYSIS_MODE) returns "issues"
      projectBuilder.build(projectContext)
    }

  }

}
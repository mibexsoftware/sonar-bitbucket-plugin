package ch.mibex.bitbucket.sonar.review

import org.sonar.api.batch.bootstrap.ProjectBuilder


class PullRequestProjectBuilder(gitBaseDirResolver: GitBaseDirResolver) extends ProjectBuilder {

  override def build(context: ProjectBuilder.Context): Unit = {
    gitBaseDirResolver.init(context.projectReactor().getRoot.getBaseDir)
  }

}

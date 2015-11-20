
package ch.mibex.bitbucket.sonar.utils

import ch.mibex.bitbucket.sonar.SonarBBPlugin

object LogUtils {
  
  def f(msg: String): String = s"${SonarBBPlugin.PluginLogPrefix} $msg"

}

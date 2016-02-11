package ch.mibex.bitbucket.sonar.utils

import ch.mibex.bitbucket.sonar.SonarBBPlugin


object StringUtils {

  def pluralise(word: String, n: Long): String = pluralise(word, word + 's', n)

  def pluralise(singular: String, plural: String, n: Long): String = {
    require(n >= 0, s"${SonarBBPlugin.PluginLogPrefix} Invalid parameter $n to pluralise")
    if (n > 1) plural else singular
  }

}

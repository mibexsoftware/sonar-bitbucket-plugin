package ch.mibex.bitbucket.sonar.utils


object StringUtils {

  def pluralise(word: String, n: Long): String = pluralise(word, word + 's', n)

  def pluralise(singular: String, plural: String, n: Long): String = {
    require(n >= 0)
    if (n > 1) plural else singular
  }

}

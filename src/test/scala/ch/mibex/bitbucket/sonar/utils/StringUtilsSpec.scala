package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class StringUtilsSpec extends Specification {

  "pluralise" should {

    "not pluralise when zero elements" in {
      StringUtils.pluralise("issue", 0) must_== "issue"
    }

    "not pluralise when only 1 element" in {
      StringUtils.pluralise("issue", 1) must_== "issue"
    }

    "attach s to the word if more than 1 element" in {
      StringUtils.pluralise("issue", 2) must_== "issues"
    }

    "not pluralise when zero elements" in {
      StringUtils.pluralise("bus", "buses", 0) must_== "bus"
    }

    "not pluralise when only 1 element" in {
      StringUtils.pluralise("bus", "buses", 1) must_== "bus"
    }

    "attach plural to the word if more than 1 element" in {
      StringUtils.pluralise("bus", "buses", 2) must_== "buses"
    }

  }
  
}
package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.sonar.api.rule.Severity
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import spray.json.SerializationException

@RunWith(classOf[JUnitRunner])
class LogUtilsSpec extends Specification {

  "f" should {

    "yield log message with prefix" in {
      LogUtils.f("starting plug-in...") must_== "[sonar4bitbucket] starting plug-in..."
    }

  }


}
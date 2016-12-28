package ch.mibex.bitbucket.sonar.utils

import org.junit.runner.RunWith
import org.sonar.api.rule.Severity
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import spray.json.SerializationException

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class JsonUtilsSpec extends Specification {

  "map2Json" should {

    "create valid JSON from a Map" in {
      val testMap = Map("issues" -> List(
        Map("severity" -> Severity.BLOCKER, "line" -> 13, "isNew" -> true),
        Map("severity" -> Severity.MAJOR, "line" -> 1, "isNew" -> false)
      ))
      val testJson =
        """{"issues":[{"severity":"BLOCKER","line":13,"isNew":true},{"severity":"MAJOR","line":1,"isNew":false}]}"""
      JsonUtils.map2Json(testMap) must_== testJson
    }

    "yield a serialization error when an unknown type is given" in {
      case class UnknownClassToSerialize()
      JsonUtils.map2Json(Map("t" -> UnknownClassToSerialize())) must throwA[SerializationException]
    }

    "map nested mutable map to JSON" in {
      val m = new mutable.HashMap[String, Any]()
      m += "severity" -> Severity.BLOCKER
      val testJson =
        """{"issues":{"severity":"BLOCKER"}}"""
      val testMap = Map("issues" -> m.toMap)
      JsonUtils.map2Json(testMap) must_== testJson
    }

  }

  "mapFromJson" should {

    "create a Map from JSON" in {
      val testMap = Map("issues" -> List(
        Map("severity" -> Severity.BLOCKER, "coverage" -> 0, "isNew" -> true),
        Map("severity" -> Severity.MAJOR, "coverage" -> 0, "isNew" -> false)
      ))
      val testJson =
        """{"issues":[{"severity":"BLOCKER","coverage":0.9,"isNew":true},
                     |{"severity":"MAJOR","coverage":0.5,"isNew":false}]}""".stripMargin.replaceAll("\n", "")
      JsonUtils.mapFromJson(testJson) must_== testMap
    }

  }

}
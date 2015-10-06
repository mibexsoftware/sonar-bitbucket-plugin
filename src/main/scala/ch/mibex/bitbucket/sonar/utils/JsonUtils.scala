package ch.mibex.bitbucket.sonar.utils

import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonUtils {

  // we circumvent the type-safety of Spray here but it is otherwise to cumbersome to just get a
  // few values out of a deeply nested JSON tree
  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case q: Seq[_] => seqFormat[Any].write(q)
      case m: Map[_, _] => mapFormat[String, Any].write(m.asInstanceOf[Map[String, Any]])
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case u => serializationError("Do not understand object of type " + u.getClass.getName)
    }

    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(value)
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case JsNull => null
      case x => deserializationError("Do not understand how to deserialize " + x)
    }

  }

  def map2Json(map: Map[String, Any]): String = map.toJson.compactPrint

  def mapFromJson(json: String): Map[String, Any] = json.parseJson.convertTo[Map[String, Any]]

}

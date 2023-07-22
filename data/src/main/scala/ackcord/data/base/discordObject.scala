package ackcord.data.base

import scala.language.implicitConversions

import ackcord.data.{JsonOption, UndefOr}
import io.circe._
import io.circe.syntax._

class DiscordObject(val json: Json, startCache: Map[String, Any]) {
  private[base] val cache = startCache.to(collection.mutable.Map)

  def extensionCache(s: String): Map[String, Any] = startCache.collect {
    case (k, v) if k.startsWith(s"$s.") => k.substring(s.length + 1) -> v
  }

  def retype[A <: DiscordObject](companion: DiscordObjectCompanion[A]): A =
    companion.makeRaw(json, Map.empty)

  def selectDynamic[A](name: String)(implicit decoder: Decoder[A]): A =
    cache
      .getOrElseUpdate(name, json.hcursor.get[A](name).getOrElse(throw new MissingFieldException(name, json)))
      .asInstanceOf[A]

  override def toString = s"${getClass.getSimpleName}($json)"

  def values: Seq[() => Any] = Nil
}

trait DiscordObjectCompanion[Obj <: DiscordObject] {

  implicit val codec: Codec[Obj] = Codec.from(
    Decoder[Json].map(makeRaw(_, Map.empty)),
    Encoder[Json].contramap[Obj](_.json)
  )

  def makeRaw(json: Json, cache: Map[String, Any]): Obj

  def makeRawFromFields(fields: DiscordObjectFrom*): Obj = {
    val jsonFields = fields.flatMap {
      case f: DiscordObjectFrom.MakeField => f.json.map(f.fieldName -> _).toList
      case DiscordObjectFrom.FromExtension(_, obj) =>
        obj.json.asObject.get.toList
    }

    val json = Json.obj(jsonFields: _*)
    val cache = fields.flatMap {
      case f: DiscordObjectFrom.MakeField => List(f.fieldName -> f.value)
      case DiscordObjectFrom.FromExtension(fieldName, obj) =>
        obj.cache.map(t => (fieldName + "." + t._1) -> t._2)
    }.toMap

    makeRaw(json, cache)
  }

  implicit def makeFieldOps(s: String): MakeFieldOps = new MakeFieldOps(s)
}

class MakeFieldOps(private val str: String) extends AnyVal {
  import DiscordObjectFrom.MakeField
  def :=[A](value: A)(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, Some(encoder(value)))

  def :=?[A](value: UndefOr[A])(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, value.toOption.map(encoder(_)))

  def :=?[A](value: JsonOption[A])(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, if (value.isUndefined) None else Some(value.toOption.asJson))
}

sealed trait DiscordObjectFrom
object DiscordObjectFrom {
  sealed trait MakeField extends DiscordObjectFrom {
    type A
    val fieldName: String
    val value: A
    val json: Option[Json]
  }

  case class FromExtension(fieldName: String, obj: DiscordObject) extends DiscordObjectFrom

  object MakeField {
    case class MakeFieldImpl[A0](fieldName: String, value: A0, json: Option[Json]) extends MakeField {
      type A = A0
    }
  }

}

package ackcord.data

import scala.language.implicitConversions

import io.circe._
import io.circe.syntax._

class DiscordObject(val json: Json, startCache: Map[String, Any]) {
  private val cache = startCache.to(collection.mutable.Map)

  def selectDynamic[A](name: String)(implicit decoder: Decoder[A]): A =
    cache.getOrElseUpdate(name, json.hcursor.get[A](name)).asInstanceOf[A]
}

trait DiscordObjectCompanion[Obj <: DiscordObject] {

  implicit val codec: Codec[Obj] = Codec.from(
    Decoder[Json].map(makeRaw(_, Map.empty)),
    Encoder[Json].contramap[Obj](_.json)
  )

  def makeRaw(json: Json, cache: Map[String, Any]): Obj

  def makeRawFromFields(fields: MakeField*): Obj = {
    val json  = Json.obj(fields.flatMap(f => f.json.map(f.fieldName -> _)): _*)
    val cache = fields.map(f => f.fieldName -> f.value).toMap

    makeRaw(json, cache)
  }

  implicit def makeFieldOps(s: String): MakeFieldOps = new MakeFieldOps(s)
}

class MakeFieldOps(private val str: String) extends AnyVal {
  def :=[A](value: A)(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, Some(encoder(value)))

  def :=?[A](value: UndefOr[A])(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, value.toOption.map(encoder(_)))

  def :=?[A](value: JsonOption[A])(implicit encoder: Encoder[A]): MakeField =
    MakeField.MakeFieldImpl(str, value, if (value.isUndefined) None else Some(value.toOption.asJson))
}

sealed trait MakeField {
  type A
  val fieldName: String
  val value: A
  val json: Option[Json]
}
object MakeField {
  case class MakeFieldImpl[A0](fieldName: String, value: A0, json: Option[Json]) extends MakeField {
    type A = A0
  }
}

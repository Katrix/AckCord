package ackcord.gateway

import io.circe.{Codec, HCursor, Json}

case class RawGatewayEvent(
    op: Int,
    d: Json,
    s: Option[Int],
    t: Option[String]
)
object RawGatewayEvent {

  //Yes, this is an ugly codec, but it is also probably a codec that will be called a lot, and I want it to be as fast as it can be
  implicit val codec: Codec[RawGatewayEvent] = Codec.from(
    (c: HCursor) => c.get[Int]("op") match {
      case Right(op) =>
        c.get[Json]("d") match {
          case Right(d) =>
            c.get[Option[Int]]("s") match {
              case Right(s) =>
                c.get[Option[String]]("t") match {
                  case Right(t) => Right(RawGatewayEvent(op, d, s, t))
                  case Left(e) => Left(e)
                }
              case Left(e) => Left(e)
            }
          case Left(e) => Left(e)
        }
      case Left(e) => Left(e)
    },
    (a: RawGatewayEvent) => {
      val listBuilder = List.newBuilder[(String, Json)]

      listBuilder += ("op", Json.fromInt(a.op))
      listBuilder += ("d", a.d)

      if (a.s.isDefined) {
        listBuilder += ("s", Json.fromInt(a.s.get))
      }

      if (a.t.isDefined) {
        listBuilder += ("s", Json.fromString(a.t.get))
      }

      Json.obj(listBuilder.result(): _*)
    }
  )
}

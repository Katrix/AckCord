package ackcord.gateway.data

import ackcord.data.base.{DiscordObject, DiscordObjectCompanion}
import io.circe.{DecodingFailure, Json}

trait GatewayEventBase[D] {
  def op: GatewayEventOp
  def d: D
}
object GatewayEventBase {
  trait UnitMixin extends GatewayEventBase[Unit] {
    def d: Unit = ()
  }

  trait DispatchMixin { self: GatewayEvent.Dispatch =>
    def event: GatewayDispatchEvent = GatewayDispatchEvent.makeFromType(d, t)
  }

  trait TopMixin { self: GatewayEvent.type =>

    def tryDecode(js: Json): Either[DecodingFailure, GatewayEventBase[_]] =
      js.hcursor.get[GatewayEventOp]("op").map {
        case GatewayEventOp.Dispatch            => GatewayEvent.Dispatch.makeRaw(js, Map.empty)
        case GatewayEventOp.Heartbeat           => GatewayEvent.Heartbeat.makeRaw(js, Map.empty)
        case GatewayEventOp.Identify            => GatewayEvent.Identify.makeRaw(js, Map.empty)
        case GatewayEventOp.UpdatePresence      => GatewayEvent.UpdatePresence.makeRaw(js, Map.empty)
        case GatewayEventOp.UpdateVoiceState    => GatewayEvent.UpdateVoiceState.makeRaw(js, Map.empty)
        case GatewayEventOp.Resume              => GatewayEvent.Resume.makeRaw(js, Map.empty)
        case GatewayEventOp.Reconnect           => GatewayEvent.Reconnect.makeRaw(js, Map.empty)
        case GatewayEventOp.RequestGuildMembers => GatewayEvent.RequestGuildMembers.makeRaw(js, Map.empty)
        case GatewayEventOp.InvalidSession      => GatewayEvent.InvalidSession.makeRaw(js, Map.empty)
        case GatewayEventOp.Hello               => GatewayEvent.Hello.makeRaw(js, Map.empty)
        case GatewayEventOp.HeartbeatACK        => GatewayEvent.HeartbeatACK.makeRaw(js, Map.empty)
        case GatewayEventOp(_)                  => GatewayEvent.Unknown.makeRaw(js, Map.empty)
      }
  }

  trait GatewayEventCompanionMixin[E <: GatewayEventBase[_] with DiscordObject] extends DiscordObjectCompanion[E] {
    self =>

    def op: GatewayEventOp

    def name: String = op.name

    def unapply(ev: GatewayEventBase[_]): Option[E] = Option.when(ev.op == op)(ev.asInstanceOf[E])
  }
}

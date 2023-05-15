package ackcord.gateway.data

import ackcord.gateway.data.GatewayEventOpMixin.GatewayEventOpOperations

import scala.language.implicitConversions

trait GatewayEventOpMixin { self: GatewayEventOp.type =>

  implicit def ops(op: GatewayEventOp): GatewayEventOpOperations = new GatewayEventOpOperations(op)
}
object GatewayEventOpMixin {
  class GatewayEventOpOperations(private val op: GatewayEventOp) extends AnyVal {
    def name: String = op match {
      case GatewayEventOp.Dispatch            => "Dispatch"
      case GatewayEventOp.Heartbeat           => "Heartbeat"
      case GatewayEventOp.Identify            => "Identify"
      case GatewayEventOp.UpdatePresence      => "UpdatePresence"
      case GatewayEventOp.UpdateVoiceState    => "UpdateVoiceState"
      case GatewayEventOp.Resume              => "Resume"
      case GatewayEventOp.Reconnect           => "Reconnect"
      case GatewayEventOp.RequestGuildMembers => "RequestGuildMembers"
      case GatewayEventOp.InvalidSession      => "InvalidSession"
      case GatewayEventOp.Hello               => "Hello"
      case GatewayEventOp.HeartbeatACK        => "HeartbeatACK"
      case GatewayEventOp(_)                  => "Unknown"
    }
  }
}

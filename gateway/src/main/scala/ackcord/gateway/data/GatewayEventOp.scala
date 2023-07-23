package ackcord.gateway.data

import ackcord.data.base.{DiscordIntEnum, DiscordIntEnumCompanion}

sealed case class GatewayEventOp private (value: Int) extends DiscordIntEnum
object GatewayEventOp extends DiscordIntEnumCompanion[GatewayEventOp] with GatewayEventOpMixin {

  val Dispatch: GatewayEventOp            = GatewayEventOp(0)
  val Heartbeat: GatewayEventOp           = GatewayEventOp(1)
  val Identify: GatewayEventOp            = GatewayEventOp(2)
  val UpdatePresence: GatewayEventOp      = GatewayEventOp(3)
  val UpdateVoiceState: GatewayEventOp    = GatewayEventOp(4)
  val Resume: GatewayEventOp              = GatewayEventOp(6)
  val Reconnect: GatewayEventOp           = GatewayEventOp(7)
  val RequestGuildMembers: GatewayEventOp = GatewayEventOp(8)
  val InvalidSession: GatewayEventOp      = GatewayEventOp(9)
  val Hello: GatewayEventOp               = GatewayEventOp(10)
  val HeartbeatACK: GatewayEventOp        = GatewayEventOp(11)

  def unknown(value: Int): GatewayEventOp = new GatewayEventOp(value)

  def values: Seq[GatewayEventOp] = Seq(
    Dispatch,
    Heartbeat,
    Identify,
    UpdatePresence,
    UpdateVoiceState,
    Resume,
    Reconnect,
    RequestGuildMembers,
    InvalidSession,
    Hello,
    HeartbeatACK
  )

}

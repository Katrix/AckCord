package ackcord.gateway

trait DisconnectBehavior
object DisconnectBehavior {
  case class FatalError(error: String)     extends DisconnectBehavior
  case class Reconnect(resumable: Boolean) extends DisconnectBehavior
  case object Done                         extends DisconnectBehavior
}

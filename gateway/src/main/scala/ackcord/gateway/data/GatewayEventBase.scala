package ackcord.gateway.data

trait GatewayEventBase[D] {
  def op: GatewayEventOp
  def d: D
}
object GatewayEventBase {
  trait UnitMixin extends GatewayEventBase[Unit] {
    def d: Unit = ()
  }
}

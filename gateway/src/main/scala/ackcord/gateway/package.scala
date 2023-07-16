package ackcord

package object gateway {

  type GatewayIntents = data.GatewayIntents
  val GatewayIntents: data.GatewayIntents.type = data.GatewayIntents

  type Context = GatewayProcess.Context
  val Context: GatewayProcess.Context.type = GatewayProcess.Context

  type ContextKey[A] = GatewayProcess.ContextKey[A]
  val ContextKey: GatewayProcess.ContextKey.type = GatewayProcess.ContextKey
}

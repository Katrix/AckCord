package ackcord.gateway.data

import ackcord.gateway.data.GatewayIntentsMixin.GatewayIntentsOperations

import scala.language.implicitConversions

trait GatewayIntentsMixin { self: GatewayIntents.type =>

  implicit def ops(intents: GatewayIntents): GatewayIntentsOperations = new GatewayIntentsOperations(intents)
}
object GatewayIntentsMixin {
  class GatewayIntentsOperations(private val intents: GatewayIntents) extends AnyVal {
    def ++(other: GatewayIntents): GatewayIntents = GatewayIntents(intents.value | other.value)
  }
}

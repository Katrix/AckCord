package ackcord.interactions.data

import ackcord.data.{Snowflake, SnowflakeCompanion}

trait SnowflakeDefs {

  type CommandId = Snowflake[ApplicationCommand]
  object CommandId extends SnowflakeCompanion[ApplicationCommand]

  type InteractionId = Snowflake[Interaction]
  object InteractionId extends SnowflakeCompanion[Interaction]
}

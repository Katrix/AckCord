package ackcord

package object data extends SnowflakeDefs with PermissionDefs {

  //TODO: Temporary to make things work
  type GuildId = RawSnowflake
  type UserId = RawSnowflake
  type ChannelId = RawSnowflake
}

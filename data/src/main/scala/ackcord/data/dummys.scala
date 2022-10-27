package ackcord.data

import io.circe.Codec

sealed trait Team
object Team {
  implicit val codec: Codec[Team] = ???
}

sealed trait GuildId
object GuildId {
  implicit val codec: Codec[GuildId] = ???
}

sealed trait GuildChannelId
object GuildChannelId {
  implicit val codec: Codec[GuildChannelId] = ???
}

sealed trait UserId
object UserId {
  implicit val codec: Codec[UserId] = ???
}


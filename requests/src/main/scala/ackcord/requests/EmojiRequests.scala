//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/EmojiRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object EmojiRequests {

  def getGuildEmojis(guildId: GuildId): Request[Unit, Seq[Emoji]] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "emojis")
        .toRequest(Method.GET)
    )

  def getGuildEmoji(guildId: GuildId, emojiId: EmojiId): Request[Unit, Emoji] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "emojis" / Parameters[
        EmojiId
      ]("emojiId", emojiId)).toRequest(Method.GET)
    )

  class CreateGuildEmojiBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The name of the emoji */
    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): CreateGuildEmojiBody = objWith(CreateGuildEmojiBody, "name", newValue)

    /** The base64 encoded image */
    @inline def image: String = selectDynamic[String]("image")

    @inline def withImage(newValue: String): CreateGuildEmojiBody = objWith(CreateGuildEmojiBody, "image", newValue)

    /** The roles that can use this emoji */
    @inline def roles: Seq[RoleId] = selectDynamic[Seq[RoleId]]("roles")

    @inline def withRoles(newValue: Seq[RoleId]): CreateGuildEmojiBody =
      objWith(CreateGuildEmojiBody, "roles", newValue)

    override def values: Seq[() => Any] = Seq(() => name, () => image, () => roles)
  }
  object CreateGuildEmojiBody extends DiscordObjectCompanion[CreateGuildEmojiBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): CreateGuildEmojiBody =
      new CreateGuildEmojiBody(json, cache)

    /**
      * @param name
      *   The name of the emoji
      * @param image
      *   The base64 encoded image
      * @param roles
      *   The roles that can use this emoji
      */
    def make20(name: String, image: String, roles: Seq[RoleId]): CreateGuildEmojiBody =
      makeRawFromFields("name" := name, "image" := image, "roles" := roles)
  }

  def createGuildEmoji(
      guildId: GuildId,
      body: CreateGuildEmojiBody,
      reason: Option[String]
  ): Request[CreateGuildEmojiBody, Emoji] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "emojis")
        .toRequest(Method.POST),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  class ModifyGuildEmojiBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The name of the emoji */
    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): ModifyGuildEmojiBody = objWith(ModifyGuildEmojiBody, "name", newValue)

    /** The roles that can use this emoji */
    @inline def roles: UndefOr[Seq[RoleId]] = selectDynamic[UndefOr[Seq[RoleId]]]("roles")

    @inline def withRoles(newValue: UndefOr[Seq[RoleId]]): ModifyGuildEmojiBody =
      objWithUndef(ModifyGuildEmojiBody, "roles", newValue)

    override def values: Seq[() => Any] = Seq(() => name, () => roles)
  }
  object ModifyGuildEmojiBody extends DiscordObjectCompanion[ModifyGuildEmojiBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): ModifyGuildEmojiBody =
      new ModifyGuildEmojiBody(json, cache)

    /**
      * @param name
      *   The name of the emoji
      * @param roles
      *   The roles that can use this emoji
      */
    def make20(
        name: String,
        roles: UndefOr[Seq[RoleId]] = UndefOrUndefined(Some("roles"))
    ): ModifyGuildEmojiBody = makeRawFromFields("name" := name, "roles" :=? roles)
  }

  def modifyGuildEmoji(
      guildId: GuildId,
      emojiId: EmojiId,
      body: ModifyGuildEmojiBody,
      reason: Option[String]
  ): Request[ModifyGuildEmojiBody, Emoji] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "emojis" / Parameters[
        EmojiId
      ]("emojiId", emojiId)).toRequest(Method.PATCH),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  def deleteGuildEmoji(guildId: GuildId, emojiId: EmojiId, reason: Option[String]): Request[Unit, Unit] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "emojis" / Parameters[
        EmojiId
      ]("emojiId", emojiId)).toRequest(Method.DELETE),
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )
}

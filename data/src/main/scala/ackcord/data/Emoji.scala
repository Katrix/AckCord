//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/Emoji.yaml

import ackcord.data.base._
import io.circe.Json

/** A custom emoji */
class Emoji(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The id of this emoji. */
  @inline def id: Option[EmojiId] = selectDynamic[Option[EmojiId]]("id")

  /** The name of this emoji. Can be None for reaction emojis */
  @inline def name: Option[String] = selectDynamic[Option[String]]("name")

  /** The roles this emoji is whitelisted to */
  @inline def roles: UndefOr[Seq[RoleId]] = selectDynamic[UndefOr[Seq[RoleId]]]("roles")

  /** The user that created this emoji */
  @inline def user: UndefOr[User] = selectDynamic[UndefOr[User]]("user")

  /** Whether this emoji must be wrapped in colons */
  @inline def requireColons: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("require_colons")

  /** Whether this emoji is managed */
  @inline def managed: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("managed")

  /** Whether this emoji is animated */
  @inline def animated: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("animated")

  /**
    * Whether this emoji can be used, may be false due to loss of Server Boosts
    */
  @inline def available: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("available")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => name,
    () => roles,
    () => user,
    () => requireColons,
    () => managed,
    () => animated,
    () => available
  )
}
object Emoji extends DiscordObjectCompanion[Emoji] {
  def makeRaw(json: Json, cache: Map[String, Any]): Emoji = new Emoji(json, cache)

  /**
    * @param id
    *   The id of this emoji.
    * @param name
    *   The name of this emoji. Can be None for reaction emojis
    * @param roles
    *   The roles this emoji is whitelisted to
    * @param user
    *   The user that created this emoji
    * @param requireColons
    *   Whether this emoji must be wrapped in colons
    * @param managed
    *   Whether this emoji is managed
    * @param animated
    *   Whether this emoji is animated
    * @param available
    *   Whether this emoji can be used, may be false due to loss of Server
    *   Boosts
    */
  def make20(
      id: Option[EmojiId],
      name: Option[String],
      roles: UndefOr[Seq[RoleId]] = UndefOrUndefined,
      user: UndefOr[User] = UndefOrUndefined,
      requireColons: UndefOr[Boolean] = UndefOrUndefined,
      managed: UndefOr[Boolean] = UndefOrUndefined,
      animated: UndefOr[Boolean] = UndefOrUndefined,
      available: UndefOr[Boolean] = UndefOrUndefined
  ): Emoji = makeRawFromFields(
    "id"              := id,
    "name"            := name,
    "roles"          :=? roles,
    "user"           :=? user,
    "require_colons" :=? requireColons,
    "managed"        :=? managed,
    "animated"       :=? animated,
    "available"      :=? available
  )

}

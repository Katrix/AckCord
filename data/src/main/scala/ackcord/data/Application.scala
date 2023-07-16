package ackcord.data

import ackcord.data.base.{DiscordIntEnum, DiscordIntEnumCompanion, DiscordObject, DiscordObjectCompanion}
import io.circe.Json

/** A Discord application

 */
class Application(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache)  {
   /** The id of the application

 */
@inline def id: Snowflake[Application] = selectDynamic[Snowflake[Application]]("id")

/** The name of the application

 */
@inline def name: String = selectDynamic[String]("name")

/** The icon of the application

 */
@inline def icon: Option[String] = selectDynamic[Option[String]]("icon")

@inline def rpcOrigins: UndefOr[Seq[String]] = selectDynamic[UndefOr[Seq[String]]]("rpc_origins")

@inline def botPublic: Boolean = selectDynamic[Boolean]("bot_public")

@inline def botRequireCodeGrant: Boolean = selectDynamic[Boolean]("bot_require_code_grant")

@inline def termsOfServiceUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("terms_of_service_url")

@inline def privacyPolicyUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("privacy_policy_url")

@inline def owner: UndefOr[Application.ApplicationOwner] = selectDynamic[UndefOr[Application.ApplicationOwner]]("owner")

@inline def verifyKey: String = selectDynamic[String]("verify_key")

@inline def team: Option[Team] = selectDynamic[Option[Team]]("team")

@inline def guildId: UndefOr[GuildId] = selectDynamic[UndefOr[GuildId]]("guild_id")

@inline def primarySkuId: UndefOr[RawSnowflake] = selectDynamic[UndefOr[RawSnowflake]]("primary_sku_id")

@inline def slug: UndefOr[String] = selectDynamic[UndefOr[String]]("slug")

@inline def coverImage: UndefOr[String] = selectDynamic[UndefOr[String]]("cover_image")

@inline def flags: UndefOr[Application.Flags] = selectDynamic[UndefOr[Application.Flags]]("flags")

@inline def tags: UndefOr[Seq[String]] = selectDynamic[UndefOr[Seq[String]]]("tags")

@inline def installParams: UndefOr[Application.InstallParams] = selectDynamic[UndefOr[Application.InstallParams]]("install_params")

@inline def customInstallUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("custom_install_url")
}
object Application extends DiscordObjectCompanion[Application]  {
  def makeRaw(json: Json, cache: Map[String, Any]): Application = new Application(json, cache)

  /** 
*@param id The id of the application
*@param name The name of the application
*@param icon The icon of the application
 */ def make20(id: Snowflake[Application], name: String, icon: Option[String], rpcOrigins: UndefOr[Seq[String]], botPublic: Boolean, botRequireCodeGrant: Boolean, termsOfServiceUrl: UndefOr[String], privacyPolicyUrl: UndefOr[String], owner: UndefOr[Application.ApplicationOwner], verifyKey: String, team: Option[Team], guildId: UndefOr[GuildId], primarySkuId: UndefOr[RawSnowflake], slug: UndefOr[String], coverImage: UndefOr[String], flags: UndefOr[Application.Flags], tags: UndefOr[Seq[String]], installParams: UndefOr[Application.InstallParams], customInstallUrl: UndefOr[String]): Application = makeRawFromFields("id" := id, "name" := name, "icon" := icon, "rpc_origins" :=? rpcOrigins, "bot_public" := botPublic, "bot_require_code_grant" := botRequireCodeGrant, "terms_of_service_url" :=? termsOfServiceUrl, "privacy_policy_url" :=? privacyPolicyUrl, "owner" :=? owner, "verify_key" := verifyKey, "team" := team, "guild_id" :=? guildId, "primary_sku_id" :=? primarySkuId, "slug" :=? slug, "cover_image" :=? coverImage, "flags" :=? flags, "tags" :=? tags, "install_params" :=? installParams, "custom_install_url" :=? customInstallUrl)

  

sealed case class Flags private(value: Int) extends DiscordIntEnum
object Flags extends DiscordIntEnumCompanion[Flags]  {

  val GATEWAY_PRESENCE: Flags = Flags(1 << 12)
  val GATEWAY_PRESENCE_LIMITED: Flags = Flags(1 << 13)
  val GATEWAY_GUILD_MEMBERS: Flags = Flags(1 << 14)
  val GATEWAY_GUILD_MEMBERS_LIMITED: Flags = Flags(1 << 15)
  val VERIFICATION_PENDING_GUILD_LIMIT: Flags = Flags(1 << 16)
  val EMBEDDED: Flags = Flags(1 << 17)
  val GATEWAY_MESSAGE_CONTENT: Flags = Flags(1 << 18)
  val GATEWAY_MESSAGE_CONTENT_LIMITED: Flags = Flags(1 << 19)
  val APPLICATION_COMMAND_BADGE: Flags = Flags(1 << 23)
  
  def unknown(value: Int): Flags = new Flags(value)
  
  def values: Seq[Flags] = Seq(GATEWAY_PRESENCE, GATEWAY_PRESENCE_LIMITED, GATEWAY_GUILD_MEMBERS, GATEWAY_GUILD_MEMBERS_LIMITED, VERIFICATION_PENDING_GUILD_LIMIT, EMBEDDED, GATEWAY_MESSAGE_CONTENT, GATEWAY_MESSAGE_CONTENT_LIMITED, APPLICATION_COMMAND_BADGE)

  
}



class InstallParams(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache)  {
   @inline def scopes: Seq[String] = selectDynamic[Seq[String]]("scopes")

@inline def permissions: Permission = selectDynamic[Permission]("permissions")
}
object InstallParams extends DiscordObjectCompanion[InstallParams]  {
  def makeRaw(json: Json, cache: Map[String, Any]): InstallParams = new InstallParams(json, cache)

   def make20(scopes: Seq[String], permissions: Permission): InstallParams = makeRawFromFields("scopes" := scopes, "permissions" := permissions)

  
}



class ApplicationOwner(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache)  {
   @inline def avatar: Option[String] = selectDynamic[Option[String]]("avatar")

@inline def discriminator: String = selectDynamic[String]("discriminator")

@inline def flags: Int = selectDynamic[Int]("flags")

@inline def id: UserId = selectDynamic[UserId]("id")

@inline def username: String = selectDynamic[String]("username")
}
object ApplicationOwner extends DiscordObjectCompanion[ApplicationOwner]  {
  def makeRaw(json: Json, cache: Map[String, Any]): ApplicationOwner = new ApplicationOwner(json, cache)

   def make20(avatar: Option[String], discriminator: String, flags: Int, id: UserId, username: String): ApplicationOwner = makeRawFromFields("avatar" := avatar, "discriminator" := discriminator, "flags" := flags, "id" := id, "username" := username)

  
}
}
package ackcord.data

import ackcord.data.raw.PartialUser

/**
  * @param id Id of the application.
  * @param name Name of the application.
  * @param icon Icon hash for the application.
  * @param description Description of the application.
  * @param rpcOrigins A sequence of RPC origin urls, if RPC is enabled.
  * @param botPublic If other users can add this bot to guilds.
  * @param botRequireCodeGrant If the bot requires the full OAuth2 code grant flow to join a guild.
  * @param termsOfServiceUrl URL for the application's ToS
  * @param privacyPolicyUrl URL for the application's privacy policy
  * @param owner The owner of the application
  * @param summary If this application is a game sold on Discord, then this is a short summary
  * @param verifyKey Hex encoded hey for verification in interactions and the GameSDK's GetTicket
  * @param team The team the application belongs to, if it belongs to one
  * @param guildId If this application is a game sold on Discord, the guild id it has been linked to
  * @param primarySkuId If this application is a game sold on Discord, this is the id of the "Game SKU" that is created if it exists
  * @param slug If this application is a game sold on Discord, an URL slug that links to the store page
  * @param coverImage This application's default rich presence invite cover image hash
  * @param flags This application's public flags
  */
case class Application(
    id: ApplicationId,
    name: String,
    icon: Option[String],
    description: String,
    rpcOrigins: Option[Seq[String]],
    botPublic: Boolean,
    botRequireCodeGrant: Boolean,
    termsOfServiceUrl: Option[String],
    privacyPolicyUrl: Option[String],
    owner: PartialUser,
    summary: String,
    verifyKey: String,
    team: Option[Team],
    guildId: Option[GuildId],
    primarySkuId: Option[RawSnowflake],
    slug: Option[String],
    coverImage: Option[String],
    flags: ApplicationFlags
)

case class PartialApplication(
    id: ApplicationId,
    name: String,
    icon: Option[String],
    description: Option[String],
    rpcOrigins: Option[Seq[String]],
    botPublic: Option[Boolean],
    botRequireCodeGrant: Option[Boolean],
    termsOfServiceUrl: Option[String],
    privacyPolicyUrl: Option[String],
    owner: Option[PartialUser],
    summary: Option[String],
    verifyKey: Option[String],
    team: Option[Team],
    guildId: Option[GuildId],
    primarySkuId: Option[RawSnowflake],
    slug: Option[String],
    coverImage: Option[String],
    flags: Option[ApplicationFlags]
)

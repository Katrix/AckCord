//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/Application.yaml

import ackcord.data.base._
import io.circe.Json

/** A Discord application */
class Application(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The id of the app */
  @inline def id: ApplicationId = selectDynamic[ApplicationId]("id")

  /** The name of the app */
  @inline def name: String = selectDynamic[String]("name")

  /** The icon hash of the app */
  @inline def icon: Option[ImageHash] = selectDynamic[Option[ImageHash]]("icon")

  /** The description of the app */
  @inline def description: String = selectDynamic[String]("description")

  /** An array of rpc origin urls, if rpc is enabled */
  @inline def rpcOrigins: UndefOr[Seq[String]] = selectDynamic[UndefOr[Seq[String]]]("rpc_origins")

  /** When false only app owner can join the app's bot to guilds */
  @inline def botPublic: Boolean = selectDynamic[Boolean]("bot_public")

  /**
    * When true the app's bot will only join upon completion of the full oauth2
    * code grant flow
    */
  @inline def botRequireCodeGrant: Boolean = selectDynamic[Boolean]("bot_require_code_grant")

  /** The url of the app's terms of service */
  @inline def termsOfServiceUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("terms_of_service_url")

  /** The url of the app's privacy policy */
  @inline def privacyPolicyUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("privacy_policy_url")

  /** Partial user object containing info on the owner of the application */
  @inline def owner: UndefOr[Application.ApplicationOwner] =
    selectDynamic[UndefOr[Application.ApplicationOwner]]("owner")

  /**
    * The hex encoded key for verification in interactions and the GameSDK's
    * GetTicket
    */
  @inline def verifyKey: String = selectDynamic[String]("verify_key")

  /**
    * If the application belongs to a team, this will be a list of the members
    * of that team
    */
  @inline def team: Option[Application.Team] = selectDynamic[Option[Application.Team]]("team")

  /** Guild associated with the app. For example, a developer support server. */
  @inline def guildId: UndefOr[GuildId] = selectDynamic[UndefOr[GuildId]]("guild_id")

  /** A partial object of the associated guild */
  @inline def guild: UndefOr[Application.ApplicationGuild] =
    selectDynamic[UndefOr[Application.ApplicationGuild]]("guild")

  /**
    * If this application is a game sold on Discord, this field will be the id
    * of the "Game SKU" that is created, if exists
    */
  @inline def primarySkuId: UndefOr[RawSnowflake] = selectDynamic[UndefOr[RawSnowflake]]("primary_sku_id")

  /**
    * If this application is a game sold on Discord, this field will be the URL
    * slug that links to the store page
    */
  @inline def slug: UndefOr[String] = selectDynamic[UndefOr[String]]("slug")

  /** The application's default rich presence invite cover image hash */
  @inline def coverImage: UndefOr[ImageHash] = selectDynamic[UndefOr[ImageHash]]("cover_image")

  /** The application's public flags */
  @inline def flags: UndefOr[Application.Flags] = selectDynamic[UndefOr[Application.Flags]]("flags")

  /** An approximate count of the app's guild membership */
  @inline def approximateGuildCount: UndefOr[Int] = selectDynamic[UndefOr[Int]]("approximate_guild_count")

  /**
    * Up to 5 tags describing the content and functionality of the application
    */
  @inline def tags: UndefOr[Seq[String]] = selectDynamic[UndefOr[Seq[String]]]("tags")

  /**
    * Settings for the application's default in-app authorization link, if
    * enabled
    */
  @inline def installParams: UndefOr[Application.InstallParams] =
    selectDynamic[UndefOr[Application.InstallParams]]("install_params")

  /** The application's default custom authorization link, if enabled */
  @inline def customInstallUrl: UndefOr[String] = selectDynamic[UndefOr[String]]("custom_install_url")

  /**
    * The application's role connection verification entry point, which when
    * configured will render the app as a verification method in the guild role
    * verification configuration
    */
  @inline def roleConnectionsVerificationUrl: UndefOr[String] =
    selectDynamic[UndefOr[String]]("role_connections_verification_url")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => name,
    () => icon,
    () => description,
    () => rpcOrigins,
    () => botPublic,
    () => botRequireCodeGrant,
    () => termsOfServiceUrl,
    () => privacyPolicyUrl,
    () => owner,
    () => verifyKey,
    () => team,
    () => guildId,
    () => guild,
    () => primarySkuId,
    () => slug,
    () => coverImage,
    () => flags,
    () => approximateGuildCount,
    () => tags,
    () => installParams,
    () => customInstallUrl,
    () => roleConnectionsVerificationUrl
  )
}
object Application extends DiscordObjectCompanion[Application] {
  def makeRaw(json: Json, cache: Map[String, Any]): Application = new Application(json, cache)

  /**
    * @param id
    *   The id of the app
    * @param name
    *   The name of the app
    * @param icon
    *   The icon hash of the app
    * @param description
    *   The description of the app
    * @param rpcOrigins
    *   An array of rpc origin urls, if rpc is enabled
    * @param botPublic
    *   When false only app owner can join the app's bot to guilds
    * @param botRequireCodeGrant
    *   When true the app's bot will only join upon completion of the full
    *   oauth2 code grant flow
    * @param termsOfServiceUrl
    *   The url of the app's terms of service
    * @param privacyPolicyUrl
    *   The url of the app's privacy policy
    * @param owner
    *   Partial user object containing info on the owner of the application
    * @param verifyKey
    *   The hex encoded key for verification in interactions and the GameSDK's
    *   GetTicket
    * @param team
    *   If the application belongs to a team, this will be a list of the members
    *   of that team
    * @param guildId
    *   Guild associated with the app. For example, a developer support server.
    * @param guild
    *   A partial object of the associated guild
    * @param primarySkuId
    *   If this application is a game sold on Discord, this field will be the id
    *   of the "Game SKU" that is created, if exists
    * @param slug
    *   If this application is a game sold on Discord, this field will be the
    *   URL slug that links to the store page
    * @param coverImage
    *   The application's default rich presence invite cover image hash
    * @param flags
    *   The application's public flags
    * @param approximateGuildCount
    *   An approximate count of the app's guild membership
    * @param tags
    *   Up to 5 tags describing the content and functionality of the application
    * @param installParams
    *   Settings for the application's default in-app authorization link, if
    *   enabled
    * @param customInstallUrl
    *   The application's default custom authorization link, if enabled
    * @param roleConnectionsVerificationUrl
    *   The application's role connection verification entry point, which when
    *   configured will render the app as a verification method in the guild
    *   role verification configuration
    */
  def make20(
      id: ApplicationId,
      name: String,
      icon: Option[ImageHash],
      description: String,
      rpcOrigins: UndefOr[Seq[String]] = UndefOrUndefined,
      botPublic: Boolean,
      botRequireCodeGrant: Boolean,
      termsOfServiceUrl: UndefOr[String] = UndefOrUndefined,
      privacyPolicyUrl: UndefOr[String] = UndefOrUndefined,
      owner: UndefOr[Application.ApplicationOwner] = UndefOrUndefined,
      verifyKey: String,
      team: Option[Application.Team],
      guildId: UndefOr[GuildId] = UndefOrUndefined,
      guild: UndefOr[Application.ApplicationGuild] = UndefOrUndefined,
      primarySkuId: UndefOr[RawSnowflake] = UndefOrUndefined,
      slug: UndefOr[String] = UndefOrUndefined,
      coverImage: UndefOr[ImageHash] = UndefOrUndefined,
      flags: UndefOr[Application.Flags] = UndefOrUndefined,
      approximateGuildCount: UndefOr[Int] = UndefOrUndefined,
      tags: UndefOr[Seq[String]] = UndefOrUndefined,
      installParams: UndefOr[Application.InstallParams] = UndefOrUndefined,
      customInstallUrl: UndefOr[String] = UndefOrUndefined,
      roleConnectionsVerificationUrl: UndefOr[String] = UndefOrUndefined
  ): Application = makeRawFromFields(
    "id"                                 := id,
    "name"                               := name,
    "icon"                               := icon,
    "description"                        := description,
    "rpc_origins"                       :=? rpcOrigins,
    "bot_public"                         := botPublic,
    "bot_require_code_grant"             := botRequireCodeGrant,
    "terms_of_service_url"              :=? termsOfServiceUrl,
    "privacy_policy_url"                :=? privacyPolicyUrl,
    "owner"                             :=? owner,
    "verify_key"                         := verifyKey,
    "team"                               := team,
    "guild_id"                          :=? guildId,
    "guild"                             :=? guild,
    "primary_sku_id"                    :=? primarySkuId,
    "slug"                              :=? slug,
    "cover_image"                       :=? coverImage,
    "flags"                             :=? flags,
    "approximate_guild_count"           :=? approximateGuildCount,
    "tags"                              :=? tags,
    "install_params"                    :=? installParams,
    "custom_install_url"                :=? customInstallUrl,
    "role_connections_verification_url" :=? roleConnectionsVerificationUrl
  )

  sealed case class Flags private (value: Int) extends DiscordEnum[Int]
  object Flags extends DiscordEnumCompanion[Int, Flags] {

    val GATEWAY_PRESENCE: Flags                 = Flags(1 << 12)
    val GATEWAY_PRESENCE_LIMITED: Flags         = Flags(1 << 13)
    val GATEWAY_GUILD_MEMBERS: Flags            = Flags(1 << 14)
    val GATEWAY_GUILD_MEMBERS_LIMITED: Flags    = Flags(1 << 15)
    val VERIFICATION_PENDING_GUILD_LIMIT: Flags = Flags(1 << 16)
    val EMBEDDED: Flags                         = Flags(1 << 17)
    val GATEWAY_MESSAGE_CONTENT: Flags          = Flags(1 << 18)
    val GATEWAY_MESSAGE_CONTENT_LIMITED: Flags  = Flags(1 << 19)
    val APPLICATION_COMMAND_BADGE: Flags        = Flags(1 << 23)

    def unknown(value: Int): Flags = new Flags(value)

    def values: Seq[Flags] = Seq(
      GATEWAY_PRESENCE,
      GATEWAY_PRESENCE_LIMITED,
      GATEWAY_GUILD_MEMBERS,
      GATEWAY_GUILD_MEMBERS_LIMITED,
      VERIFICATION_PENDING_GUILD_LIMIT,
      EMBEDDED,
      GATEWAY_MESSAGE_CONTENT,
      GATEWAY_MESSAGE_CONTENT_LIMITED,
      APPLICATION_COMMAND_BADGE
    )

    implicit class FlagsBitFieldOps(private val here: Flags) extends AnyVal {
      def toInt: Int = here.value

      def ++(there: Flags): Flags = Flags(here.value | there.value)

      def --(there: Flags): Flags = Flags(here.value & ~there.value)

      def isNone: Boolean = here.value == 0
    }
  }

  class ApplicationGuild(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    override def values: Seq[() => Any] = Seq()
  }
  object ApplicationGuild extends DiscordObjectCompanion[ApplicationGuild] {
    def makeRaw(json: Json, cache: Map[String, Any]): ApplicationGuild = new ApplicationGuild(json, cache)

    def make20(): ApplicationGuild = makeRawFromFields()

  }

  class InstallParams(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
    @inline def scopes: Seq[String] = selectDynamic[Seq[String]]("scopes")

    @inline def permissions: Permissions = selectDynamic[Permissions]("permissions")

    override def values: Seq[() => Any] = Seq(() => scopes, () => permissions)
  }
  object InstallParams extends DiscordObjectCompanion[InstallParams] {
    def makeRaw(json: Json, cache: Map[String, Any]): InstallParams = new InstallParams(json, cache)

    def make20(scopes: Seq[String], permissions: Permissions): InstallParams =
      makeRawFromFields("scopes" := scopes, "permissions" := permissions)

  }

  class ApplicationOwner(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
    @inline def avatar: Option[String] = selectDynamic[Option[String]]("avatar")

    @inline def discriminator: String = selectDynamic[String]("discriminator")

    @inline def flags: Int = selectDynamic[Int]("flags")

    @inline def id: UserId = selectDynamic[UserId]("id")

    @inline def username: String = selectDynamic[String]("username")

    override def values: Seq[() => Any] = Seq(() => avatar, () => discriminator, () => flags, () => id, () => username)
  }
  object ApplicationOwner extends DiscordObjectCompanion[ApplicationOwner] {
    def makeRaw(json: Json, cache: Map[String, Any]): ApplicationOwner = new ApplicationOwner(json, cache)

    def make20(
        avatar: Option[String],
        discriminator: String,
        flags: Int,
        id: UserId,
        username: String
    ): ApplicationOwner = makeRawFromFields(
      "avatar"        := avatar,
      "discriminator" := discriminator,
      "flags"         := flags,
      "id"            := id,
      "username"      := username
    )

  }

  class Team(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** A hash of the image of the team's icon */
    @inline def icon: ImageHash = selectDynamic[ImageHash]("icon")

    /** The unique id of the team */
    @inline def id: Snowflake[Team] = selectDynamic[Snowflake[Team]]("id")

    /** The members of the team */
    @inline def members: Seq[Team.TeamMember] = selectDynamic[Seq[Team.TeamMember]]("members")

    /** The name of the team */
    @inline def name: String = selectDynamic[String]("name")

    /** The user id of the current team owner */
    @inline def ownerUserId: UserId = selectDynamic[UserId]("owner_user_id")

    override def values: Seq[() => Any] = Seq(() => icon, () => id, () => members, () => name, () => ownerUserId)
  }
  object Team extends DiscordObjectCompanion[Team] {
    def makeRaw(json: Json, cache: Map[String, Any]): Team = new Team(json, cache)

    /**
      * @param icon
      *   A hash of the image of the team's icon
      * @param id
      *   The unique id of the team
      * @param members
      *   The members of the team
      * @param name
      *   The name of the team
      * @param ownerUserId
      *   The user id of the current team owner
      */
    def make20(
        icon: ImageHash,
        id: Snowflake[Team],
        members: Seq[Team.TeamMember],
        name: String,
        ownerUserId: UserId
    ): Team = makeRawFromFields(
      "icon"          := icon,
      "id"            := id,
      "members"       := members,
      "name"          := name,
      "owner_user_id" := ownerUserId
    )

    class TeamMember(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

      /** The user's membership state on the team */
      @inline def membershipState: Team.TeamMembershipState =
        selectDynamic[Team.TeamMembershipState]("membership_state")

      /** Will always be ["*"] */
      @inline def permissions: Seq[String] = selectDynamic[Seq[String]]("permissions")

      /** the id of the parent team of which they are a member */
      @inline def teamId: Snowflake[Team] = selectDynamic[Snowflake[Team]]("team_id")

      /** The avatar, discriminator, id, and username of the user */
      @inline def user: Team.TeamUser = selectDynamic[Team.TeamUser]("user")

      override def values: Seq[() => Any] = Seq(() => membershipState, () => permissions, () => teamId, () => user)
    }
    object TeamMember extends DiscordObjectCompanion[TeamMember] {
      def makeRaw(json: Json, cache: Map[String, Any]): TeamMember = new TeamMember(json, cache)

      /**
        * @param membershipState
        *   The user's membership state on the team
        * @param permissions
        *   Will always be ["*"]
        * @param teamId
        *   the id of the parent team of which they are a member
        * @param user
        *   The avatar, discriminator, id, and username of the user
        */
      def make20(
          membershipState: Team.TeamMembershipState,
          permissions: Seq[String],
          teamId: Snowflake[Team],
          user: Team.TeamUser
      ): TeamMember = makeRawFromFields(
        "membership_state" := membershipState,
        "permissions"      := permissions,
        "team_id"          := teamId,
        "user"             := user
      )

    }

    sealed case class TeamMembershipState private (value: Int) extends DiscordEnum[Int]
    object TeamMembershipState extends DiscordEnumCompanion[Int, TeamMembershipState] {

      val INVITED: TeamMembershipState  = TeamMembershipState(1)
      val ACCEPTED: TeamMembershipState = TeamMembershipState(2)

      def unknown(value: Int): TeamMembershipState = new TeamMembershipState(value)

      def values: Seq[TeamMembershipState] = Seq(INVITED, ACCEPTED)

    }

    class TeamUser(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
      @inline def id: UserId = selectDynamic[UserId]("id")

      @inline def username: String = selectDynamic[String]("username")

      @inline def avatar: Option[ImageHash] = selectDynamic[Option[ImageHash]]("avatar")

      @inline def discriminator: String = selectDynamic[String]("discriminator")

      override def values: Seq[() => Any] = Seq(() => id, () => username, () => avatar, () => discriminator)
    }
    object TeamUser extends DiscordObjectCompanion[TeamUser] {
      def makeRaw(json: Json, cache: Map[String, Any]): TeamUser = new TeamUser(json, cache)

      def make20(id: UserId, username: String, avatar: Option[ImageHash], discriminator: String): TeamUser =
        makeRawFromFields("id" := id, "username" := username, "avatar" := avatar, "discriminator" := discriminator)

    }
  }
}

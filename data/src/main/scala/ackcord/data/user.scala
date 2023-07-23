//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/user.yaml

import ackcord.data.base._
import io.circe.Json

sealed trait UserOrRole

sealed trait MessageAuthor extends DiscordObject

object MessageAuthor extends DiscordObjectCompanion[MessageAuthor] {
  def makeRaw(json: Json, cache: Map[String, Any]): MessageAuthor =
    if (json.hcursor.get[String]("discriminator").isRight) User.makeRaw(json, cache)
    else WebhookAuthor.makeRaw(json, cache)
}

sealed case class Status private (value: String) extends DiscordEnum[String]
object Status extends DiscordEnumCompanion[String, Status] {

  val Online: Status       = Status("online")
  val DoNotDisturb: Status = Status("dnd")
  val Idle: Status         = Status("idle")
  val Invisible: Status    = Status("invisible")
  val Offline: Status      = Status("Offline")

  def unknown(value: String): Status = new Status(value)

  def values: Seq[Status] = Seq(Online, DoNotDisturb, Idle, Invisible, Offline)

}

class WebhookAuthor(json: Json, cache: Map[String, Any] = Map.empty)
    extends DiscordObject(json, cache)
    with MessageAuthor {
  @inline def id: WebhookId = selectDynamic[WebhookId]("id")

  @inline def username: String = selectDynamic[String]("username")

  @inline def avatar: Option[ImageHash] = selectDynamic[Option[ImageHash]]("avatar")

  override def values: Seq[() => Any] = Seq(() => id, () => username, () => avatar)
}
object WebhookAuthor extends DiscordObjectCompanion[WebhookAuthor] {
  def makeRaw(json: Json, cache: Map[String, Any]): WebhookAuthor = new WebhookAuthor(json, cache)

  def make20(id: WebhookId, username: String, avatar: Option[ImageHash]): WebhookAuthor =
    makeRawFromFields("id" := id, "username" := username, "avatar" := avatar)

}

/**
  * Users in Discord are generally considered the base entity. Users can spawn
  * across the entire platform, be members of guilds, participate in text and
  * voice chat, and much more. Users are separated by a distinction of "bot" vs
  * "normal." Although they are similar, bot users are automated users that are
  * "owned" by another user. Unlike normal users, bot users do not have a
  * limitation on the number of Guilds they can be a part of.
  */
class User(json: Json, cache: Map[String, Any] = Map.empty)
    extends DiscordObject(json, cache)
    with UserOrRole
    with MessageAuthor {

  /** The user's id */
  @inline def id: UserId = selectDynamic[UserId]("id")

  /** The user's username, not unique across the platform */
  @inline def username: String = selectDynamic[String]("username")

  /** The user's Discord-tag */
  @inline def discriminator: String = selectDynamic[String]("discriminator")

  /**
    * The user's display name, if it is set. For bots, this is the application
    * name
    */
  @inline def globalName: Option[String] = selectDynamic[Option[String]]("global_name")

  /** The user's avatar hash */
  @inline def avatar: Option[ImageHash] = selectDynamic[Option[ImageHash]]("avatar")

  /** Whether the user belongs to an OAuth2 application */
  @inline def bot: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("bot")

  /**
    * Whether the user is an Official Discord System user (part of the urgent
    * message system)
    */
  @inline def system: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("system")

  /** Whether the user has two factor enabled on their account */
  @inline def mfaEnabled: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("mfa_enabled")

  /** The user's banner hash */
  @inline def banner: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("banner")

  /**
    * The user's banner color encoded as an integer representation of
    * hexadecimal color code
    */
  @inline def accentColor: JsonOption[Int] = selectDynamic[JsonOption[Int]]("accent_color")

  /** The user's chosen language option */
  @inline def locale: UndefOr[String] = selectDynamic[UndefOr[String]]("locale")

  /** Whether the email on this account has been verified */
  @inline def verified: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("verified")

  /** The user's email */
  @inline def email: JsonOption[String] = selectDynamic[JsonOption[String]]("email")

  /** The flags on a user's account */
  @inline def flags: UndefOr[User.UserFlags] = selectDynamic[UndefOr[User.UserFlags]]("flags")

  /** The type of Nitro subscription on a user's account */
  @inline def premiumType: UndefOr[User.PremiumType] = selectDynamic[UndefOr[User.PremiumType]]("premium_type")

  /** The public flags on a user's account */
  @inline def publicFlags: UndefOr[User.UserFlags] = selectDynamic[UndefOr[User.UserFlags]]("public_flags")

  /** The user's avatar decoration hash */
  @inline def avatarDecoration: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("avatar_decoration")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => username,
    () => discriminator,
    () => globalName,
    () => avatar,
    () => bot,
    () => system,
    () => mfaEnabled,
    () => banner,
    () => accentColor,
    () => locale,
    () => verified,
    () => email,
    () => flags,
    () => premiumType,
    () => publicFlags,
    () => avatarDecoration
  )
}
object User extends DiscordObjectCompanion[User] {
  def makeRaw(json: Json, cache: Map[String, Any]): User = new User(json, cache)

  /**
    * @param id
    *   The user's id
    * @param username
    *   The user's username, not unique across the platform
    * @param discriminator
    *   The user's Discord-tag
    * @param globalName
    *   The user's display name, if it is set. For bots, this is the application
    *   name
    * @param avatar
    *   The user's avatar hash
    * @param bot
    *   Whether the user belongs to an OAuth2 application
    * @param system
    *   Whether the user is an Official Discord System user (part of the urgent
    *   message system)
    * @param mfaEnabled
    *   Whether the user has two factor enabled on their account
    * @param banner
    *   The user's banner hash
    * @param accentColor
    *   The user's banner color encoded as an integer representation of
    *   hexadecimal color code
    * @param locale
    *   The user's chosen language option
    * @param verified
    *   Whether the email on this account has been verified
    * @param email
    *   The user's email
    * @param flags
    *   The flags on a user's account
    * @param premiumType
    *   The type of Nitro subscription on a user's account
    * @param publicFlags
    *   The public flags on a user's account
    * @param avatarDecoration
    *   The user's avatar decoration hash
    */
  def make20(
      id: UserId,
      username: String,
      discriminator: String,
      globalName: Option[String],
      avatar: Option[ImageHash],
      bot: UndefOr[Boolean] = UndefOrUndefined,
      system: UndefOr[Boolean] = UndefOrUndefined,
      mfaEnabled: UndefOr[Boolean] = UndefOrUndefined,
      banner: JsonOption[ImageHash] = JsonUndefined,
      accentColor: JsonOption[Int] = JsonUndefined,
      locale: UndefOr[String] = UndefOrUndefined,
      verified: UndefOr[Boolean] = UndefOrUndefined,
      email: JsonOption[String] = JsonUndefined,
      flags: UndefOr[User.UserFlags] = UndefOrUndefined,
      premiumType: UndefOr[User.PremiumType] = UndefOrUndefined,
      publicFlags: UndefOr[User.UserFlags] = UndefOrUndefined,
      avatarDecoration: JsonOption[ImageHash] = JsonUndefined
  ): User = makeRawFromFields(
    "id"                 := id,
    "username"           := username,
    "discriminator"      := discriminator,
    "global_name"        := globalName,
    "avatar"             := avatar,
    "bot"               :=? bot,
    "system"            :=? system,
    "mfa_enabled"       :=? mfaEnabled,
    "banner"            :=? banner,
    "accent_color"      :=? accentColor,
    "locale"            :=? locale,
    "verified"          :=? verified,
    "email"             :=? email,
    "flags"             :=? flags,
    "premium_type"      :=? premiumType,
    "public_flags"      :=? publicFlags,
    "avatar_decoration" :=? avatarDecoration
  )

  /**
    * Users in Discord are generally considered the base entity. Users can spawn
    * across the entire platform, be members of guilds, participate in text and
    * voice chat, and much more. Users are separated by a distinction of "bot"
    * vs "normal." Although they are similar, bot users are automated users that
    * are "owned" by another user. Unlike normal users, bot users do not have a
    * limitation on the number of Guilds they can be a part of.
    */
  class Partial(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache)
      with UserOrRole
      with MessageAuthor {

    /** The user's id */
    @inline def id: UserId = selectDynamic[UserId]("id")

    /** The user's username, not unique across the platform */
    @inline def username: UndefOr[String] = selectDynamic[UndefOr[String]]("username")

    /** The user's Discord-tag */
    @inline def discriminator: UndefOr[String] = selectDynamic[UndefOr[String]]("discriminator")

    /**
      * The user's display name, if it is set. For bots, this is the application
      * name
      */
    @inline def globalName: JsonOption[String] = selectDynamic[JsonOption[String]]("global_name")

    /** The user's avatar hash */
    @inline def avatar: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("avatar")

    /** Whether the user belongs to an OAuth2 application */
    @inline def bot: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("bot")

    /**
      * Whether the user is an Official Discord System user (part of the urgent
      * message system)
      */
    @inline def system: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("system")

    /** Whether the user has two factor enabled on their account */
    @inline def mfaEnabled: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("mfa_enabled")

    /** The user's banner hash */
    @inline def banner: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("banner")

    /**
      * The user's banner color encoded as an integer representation of
      * hexadecimal color code
      */
    @inline def accentColor: JsonOption[Int] = selectDynamic[JsonOption[Int]]("accent_color")

    /** The user's chosen language option */
    @inline def locale: UndefOr[String] = selectDynamic[UndefOr[String]]("locale")

    /** Whether the email on this account has been verified */
    @inline def verified: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("verified")

    /** The user's email */
    @inline def email: JsonOption[String] = selectDynamic[JsonOption[String]]("email")

    /** The flags on a user's account */
    @inline def flags: UndefOr[User.UserFlags] = selectDynamic[UndefOr[User.UserFlags]]("flags")

    /** The type of Nitro subscription on a user's account */
    @inline def premiumType: UndefOr[User.PremiumType] = selectDynamic[UndefOr[User.PremiumType]]("premium_type")

    /** The public flags on a user's account */
    @inline def publicFlags: UndefOr[User.UserFlags] = selectDynamic[UndefOr[User.UserFlags]]("public_flags")

    /** The user's avatar decoration hash */
    @inline def avatarDecoration: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("avatar_decoration")

    override def values: Seq[() => Any] = Seq(
      () => id,
      () => username,
      () => discriminator,
      () => globalName,
      () => avatar,
      () => bot,
      () => system,
      () => mfaEnabled,
      () => banner,
      () => accentColor,
      () => locale,
      () => verified,
      () => email,
      () => flags,
      () => premiumType,
      () => publicFlags,
      () => avatarDecoration
    )
  }
  object Partial extends DiscordObjectCompanion[Partial] {
    def makeRaw(json: Json, cache: Map[String, Any]): Partial = new Partial(json, cache)

    /**
      * @param id
      *   The user's id
      * @param username
      *   The user's username, not unique across the platform
      * @param discriminator
      *   The user's Discord-tag
      * @param globalName
      *   The user's display name, if it is set. For bots, this is the
      *   application name
      * @param avatar
      *   The user's avatar hash
      * @param bot
      *   Whether the user belongs to an OAuth2 application
      * @param system
      *   Whether the user is an Official Discord System user (part of the
      *   urgent message system)
      * @param mfaEnabled
      *   Whether the user has two factor enabled on their account
      * @param banner
      *   The user's banner hash
      * @param accentColor
      *   The user's banner color encoded as an integer representation of
      *   hexadecimal color code
      * @param locale
      *   The user's chosen language option
      * @param verified
      *   Whether the email on this account has been verified
      * @param email
      *   The user's email
      * @param flags
      *   The flags on a user's account
      * @param premiumType
      *   The type of Nitro subscription on a user's account
      * @param publicFlags
      *   The public flags on a user's account
      * @param avatarDecoration
      *   The user's avatar decoration hash
      */
    def make20(
        id: UserId,
        username: UndefOr[String] = UndefOrUndefined,
        discriminator: UndefOr[String] = UndefOrUndefined,
        globalName: JsonOption[String] = JsonUndefined,
        avatar: JsonOption[ImageHash] = JsonUndefined,
        bot: UndefOr[Boolean] = UndefOrUndefined,
        system: UndefOr[Boolean] = UndefOrUndefined,
        mfaEnabled: UndefOr[Boolean] = UndefOrUndefined,
        banner: JsonOption[ImageHash] = JsonUndefined,
        accentColor: JsonOption[Int] = JsonUndefined,
        locale: UndefOr[String] = UndefOrUndefined,
        verified: UndefOr[Boolean] = UndefOrUndefined,
        email: JsonOption[String] = JsonUndefined,
        flags: UndefOr[User.UserFlags] = UndefOrUndefined,
        premiumType: UndefOr[User.PremiumType] = UndefOrUndefined,
        publicFlags: UndefOr[User.UserFlags] = UndefOrUndefined,
        avatarDecoration: JsonOption[ImageHash] = JsonUndefined
    ): Partial = makeRawFromFields(
      "id"                 := id,
      "username"          :=? username,
      "discriminator"     :=? discriminator,
      "global_name"       :=? globalName,
      "avatar"            :=? avatar,
      "bot"               :=? bot,
      "system"            :=? system,
      "mfa_enabled"       :=? mfaEnabled,
      "banner"            :=? banner,
      "accent_color"      :=? accentColor,
      "locale"            :=? locale,
      "verified"          :=? verified,
      "email"             :=? email,
      "flags"             :=? flags,
      "premium_type"      :=? premiumType,
      "public_flags"      :=? publicFlags,
      "avatar_decoration" :=? avatarDecoration
    )

    sealed case class UserFlags private (value: Int) extends DiscordEnum[Int]
    object UserFlags                                 extends DiscordEnumCompanion[Int, UserFlags] {

      /** Discord Employee */
      val STAFF: UserFlags = UserFlags(1 << 0)

      /** Partnered Server Owner */
      val PARTNER: UserFlags = UserFlags(1 << 1)

      /** HypeSquad Events Member */
      val HYPESQUAD: UserFlags = UserFlags(1 << 2)

      /** Bug Hunter Level 1 */
      val BUG_HUNTER_LEVEL_1: UserFlags = UserFlags(1 << 3)

      /** House Bravery Member */
      val HYPESQUAD_ONLINE_HOUSE_1: UserFlags = UserFlags(1 << 6)

      /** House Brilliance Member */
      val HYPESQUAD_ONLINE_HOUSE_2: UserFlags = UserFlags(1 << 7)

      /** House Balance Member */
      val HYPESQUAD_ONLINE_HOUSE_3: UserFlags = UserFlags(1 << 8)

      /** Early Nitro Supporter */
      val PREMIUM_EARLY_SUPPORTER: UserFlags = UserFlags(1 << 9)

      /** User is a team */
      val TEAM_PSEUDO_USER: UserFlags = UserFlags(1 << 10)

      /** Bug Hunter Level 2 */
      val BUG_HUNTER_LEVEL_2: UserFlags = UserFlags(1 << 14)

      /** Verified Bot */
      val VERIFIED_BOT: UserFlags = UserFlags(1 << 16)

      /** Early Verified Bot Developer */
      val VERIFIED_DEVELOPER: UserFlags = UserFlags(1 << 17)

      /** Moderator Programs Alumni */
      val CERTIFIED_MODERATOR: UserFlags = UserFlags(1 << 18)

      /**
        * Bot uses only HTTP interactions and is shown in the online member list
        */
      val BOT_HTTP_INTERACTIONS: UserFlags = UserFlags(1 << 19)

      /** User is an Active Developer */
      val ACTIVE_DEVELOPER: UserFlags = UserFlags(1 << 22)

      def unknown(value: Int): UserFlags = new UserFlags(value)

      def values: Seq[UserFlags] = Seq(
        STAFF,
        PARTNER,
        HYPESQUAD,
        BUG_HUNTER_LEVEL_1,
        HYPESQUAD_ONLINE_HOUSE_1,
        HYPESQUAD_ONLINE_HOUSE_2,
        HYPESQUAD_ONLINE_HOUSE_3,
        PREMIUM_EARLY_SUPPORTER,
        TEAM_PSEUDO_USER,
        BUG_HUNTER_LEVEL_2,
        VERIFIED_BOT,
        VERIFIED_DEVELOPER,
        CERTIFIED_MODERATOR,
        BOT_HTTP_INTERACTIONS,
        ACTIVE_DEVELOPER
      )

      implicit class UserFlagsBitFieldOps(private val here: UserFlags) extends AnyVal {
        def toInt: Int = here.value

        def ++(there: UserFlags): UserFlags = UserFlags(here.value | there.value)

        def --(there: UserFlags): UserFlags = UserFlags(here.value & ~there.value)

        def isNone: Boolean = here.value == 0
      }
    }

    /**
      * Premium types denote the level of premium a user has. Visit the Nitro
      * page to learn more about the premium plans we currently offer.
      */
    sealed case class PremiumType private (value: Int) extends DiscordEnum[Int]
    object PremiumType extends DiscordEnumCompanion[Int, PremiumType] {

      val None: PremiumType         = PremiumType(0)
      val NitroClassic: PremiumType = PremiumType(1)
      val Nitro: PremiumType        = PremiumType(2)
      val NitroBasic: PremiumType   = PremiumType(3)

      def unknown(value: Int): PremiumType = new PremiumType(value)

      def values: Seq[PremiumType] = Seq(None, NitroClassic, Nitro, NitroBasic)

    }
  }

  sealed case class UserFlags private (value: Int) extends DiscordEnum[Int]
  object UserFlags                                 extends DiscordEnumCompanion[Int, UserFlags] {

    /** Discord Employee */
    val STAFF: UserFlags = UserFlags(1 << 0)

    /** Partnered Server Owner */
    val PARTNER: UserFlags = UserFlags(1 << 1)

    /** HypeSquad Events Member */
    val HYPESQUAD: UserFlags = UserFlags(1 << 2)

    /** Bug Hunter Level 1 */
    val BUG_HUNTER_LEVEL_1: UserFlags = UserFlags(1 << 3)

    /** House Bravery Member */
    val HYPESQUAD_ONLINE_HOUSE_1: UserFlags = UserFlags(1 << 6)

    /** House Brilliance Member */
    val HYPESQUAD_ONLINE_HOUSE_2: UserFlags = UserFlags(1 << 7)

    /** House Balance Member */
    val HYPESQUAD_ONLINE_HOUSE_3: UserFlags = UserFlags(1 << 8)

    /** Early Nitro Supporter */
    val PREMIUM_EARLY_SUPPORTER: UserFlags = UserFlags(1 << 9)

    /** User is a team */
    val TEAM_PSEUDO_USER: UserFlags = UserFlags(1 << 10)

    /** Bug Hunter Level 2 */
    val BUG_HUNTER_LEVEL_2: UserFlags = UserFlags(1 << 14)

    /** Verified Bot */
    val VERIFIED_BOT: UserFlags = UserFlags(1 << 16)

    /** Early Verified Bot Developer */
    val VERIFIED_DEVELOPER: UserFlags = UserFlags(1 << 17)

    /** Moderator Programs Alumni */
    val CERTIFIED_MODERATOR: UserFlags = UserFlags(1 << 18)

    /**
      * Bot uses only HTTP interactions and is shown in the online member list
      */
    val BOT_HTTP_INTERACTIONS: UserFlags = UserFlags(1 << 19)

    /** User is an Active Developer */
    val ACTIVE_DEVELOPER: UserFlags = UserFlags(1 << 22)

    def unknown(value: Int): UserFlags = new UserFlags(value)

    def values: Seq[UserFlags] = Seq(
      STAFF,
      PARTNER,
      HYPESQUAD,
      BUG_HUNTER_LEVEL_1,
      HYPESQUAD_ONLINE_HOUSE_1,
      HYPESQUAD_ONLINE_HOUSE_2,
      HYPESQUAD_ONLINE_HOUSE_3,
      PREMIUM_EARLY_SUPPORTER,
      TEAM_PSEUDO_USER,
      BUG_HUNTER_LEVEL_2,
      VERIFIED_BOT,
      VERIFIED_DEVELOPER,
      CERTIFIED_MODERATOR,
      BOT_HTTP_INTERACTIONS,
      ACTIVE_DEVELOPER
    )

    implicit class UserFlagsBitFieldOps(private val here: UserFlags) extends AnyVal {
      def toInt: Int = here.value

      def ++(there: UserFlags): UserFlags = UserFlags(here.value | there.value)

      def --(there: UserFlags): UserFlags = UserFlags(here.value & ~there.value)

      def isNone: Boolean = here.value == 0
    }
  }

  /**
    * Premium types denote the level of premium a user has. Visit the Nitro page
    * to learn more about the premium plans we currently offer.
    */
  sealed case class PremiumType private (value: Int) extends DiscordEnum[Int]
  object PremiumType extends DiscordEnumCompanion[Int, PremiumType] {

    val None: PremiumType         = PremiumType(0)
    val NitroClassic: PremiumType = PremiumType(1)
    val Nitro: PremiumType        = PremiumType(2)
    val NitroBasic: PremiumType   = PremiumType(3)

    def unknown(value: Int): PremiumType = new PremiumType(value)

    def values: Seq[PremiumType] = Seq(None, NitroClassic, Nitro, NitroBasic)

  }
}

/** The connection object that the user has attached. */
class Connection(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** Id of the connection account */
  @inline def id: String = selectDynamic[String]("id")

  /** The username of the connection account */
  @inline def name: String = selectDynamic[String]("name")

  /** The service of this connection */
  @inline def tpe: Connection.ConnectionServiceType = selectDynamic[Connection.ConnectionServiceType]("type")

  /** Whether the connection is revoked */
  @inline def revoked: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("revoked")

  /** An array of partial server integrations */
  @inline def integrations: UndefOr[Seq[Connection.ConnectionIntegration]] =
    selectDynamic[UndefOr[Seq[Connection.ConnectionIntegration]]]("integrations")

  /** Whether the connection is verified */
  @inline def verified: Boolean = selectDynamic[Boolean]("verified")

  /** Whether friend sync is enabled for this connection */
  @inline def friendSync: Boolean = selectDynamic[Boolean]("friend_sync")

  /**
    * Whether activities related to this connection will be shown in presence
    * updates
    */
  @inline def showActivity: Boolean = selectDynamic[Boolean]("show_activity")

  /** Whether this connection has a corresponding third party OAuth2 token */
  @inline def twoWayLink: Boolean = selectDynamic[Boolean]("two_way_link")

  /** Visibility of this connection */
  @inline def visibility: Connection.ConnectionVisibility = selectDynamic[Connection.ConnectionVisibility]("visibility")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => name,
    () => tpe,
    () => revoked,
    () => integrations,
    () => verified,
    () => friendSync,
    () => showActivity,
    () => twoWayLink,
    () => visibility
  )
}
object Connection extends DiscordObjectCompanion[Connection] {
  def makeRaw(json: Json, cache: Map[String, Any]): Connection = new Connection(json, cache)

  /**
    * @param id
    *   Id of the connection account
    * @param name
    *   The username of the connection account
    * @param tpe
    *   The service of this connection
    * @param revoked
    *   Whether the connection is revoked
    * @param integrations
    *   An array of partial server integrations
    * @param verified
    *   Whether the connection is verified
    * @param friendSync
    *   Whether friend sync is enabled for this connection
    * @param showActivity
    *   Whether activities related to this connection will be shown in presence
    *   updates
    * @param twoWayLink
    *   Whether this connection has a corresponding third party OAuth2 token
    * @param visibility
    *   Visibility of this connection
    */
  def make20(
      id: String,
      name: String,
      tpe: Connection.ConnectionServiceType,
      revoked: UndefOr[Boolean] = UndefOrUndefined,
      integrations: UndefOr[Seq[Connection.ConnectionIntegration]] = UndefOrUndefined,
      verified: Boolean,
      friendSync: Boolean,
      showActivity: Boolean,
      twoWayLink: Boolean,
      visibility: Connection.ConnectionVisibility
  ): Connection = makeRawFromFields(
    "id"            := id,
    "name"          := name,
    "type"          := tpe,
    "revoked"      :=? revoked,
    "integrations" :=? integrations,
    "verified"      := verified,
    "friend_sync"   := friendSync,
    "show_activity" := showActivity,
    "two_way_link"  := twoWayLink,
    "visibility"    := visibility
  )

  class ConnectionIntegration(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    override def values: Seq[() => Any] = Seq()
  }
  object ConnectionIntegration extends DiscordObjectCompanion[ConnectionIntegration] {
    def makeRaw(json: Json, cache: Map[String, Any]): ConnectionIntegration = new ConnectionIntegration(json, cache)

    def make20(): ConnectionIntegration = makeRawFromFields()

  }

  sealed case class ConnectionServiceType private (value: String) extends DiscordEnum[String]
  object ConnectionServiceType extends DiscordEnumCompanion[String, ConnectionServiceType] {

    /** Battle.net */
    val Battlenet: ConnectionServiceType = ConnectionServiceType("battlenet")

    /** EBay */
    val Ebay: ConnectionServiceType = ConnectionServiceType("ebay")

    /** Epic Games */
    val Epicgames: ConnectionServiceType = ConnectionServiceType("epicgames")

    /** Facebook */
    val Facebook: ConnectionServiceType = ConnectionServiceType("facebook")

    /** GitHub */
    val Github: ConnectionServiceType = ConnectionServiceType("github")

    /** Instagram */
    val Instagram: ConnectionServiceType = ConnectionServiceType("instagram")

    /** League of Legends */
    val Leagueoflegends: ConnectionServiceType = ConnectionServiceType("leagueoflegends")

    /** PayPal */
    val Paypal: ConnectionServiceType = ConnectionServiceType("paypal")

    /** PlayStation Network */
    val Playstation: ConnectionServiceType = ConnectionServiceType("playstation")

    /** Reddit */
    val Reddit: ConnectionServiceType = ConnectionServiceType("reddit")

    /** Riot Games */
    val Riotgames: ConnectionServiceType = ConnectionServiceType("riotgames")

    /** Spotify */
    val Spotify: ConnectionServiceType = ConnectionServiceType("spotify")

    /** Skype */
    val Skype: ConnectionServiceType = ConnectionServiceType("skype")

    /** Steam */
    val Steam: ConnectionServiceType = ConnectionServiceType("steam")

    /** TikTok */
    val Tiktok: ConnectionServiceType = ConnectionServiceType("tiktok")

    /** Twitch */
    val Twitch: ConnectionServiceType = ConnectionServiceType("twitch")

    /** Twitter */
    val Twitter: ConnectionServiceType = ConnectionServiceType("twitter")

    /** Xbox */
    val Xbox: ConnectionServiceType = ConnectionServiceType("xbox")

    /** YouTube */
    val Youtube: ConnectionServiceType = ConnectionServiceType("youtube")

    def unknown(value: String): ConnectionServiceType = new ConnectionServiceType(value)

    def values: Seq[ConnectionServiceType] = Seq(
      Battlenet,
      Ebay,
      Epicgames,
      Facebook,
      Github,
      Instagram,
      Leagueoflegends,
      Paypal,
      Playstation,
      Reddit,
      Riotgames,
      Spotify,
      Skype,
      Steam,
      Tiktok,
      Twitch,
      Twitter,
      Xbox,
      Youtube
    )

  }

  sealed case class ConnectionVisibility private (value: Int) extends DiscordEnum[Int]
  object ConnectionVisibility                                 extends DiscordEnumCompanion[Int, ConnectionVisibility] {

    /** Invisible to everyone except the user themselves */
    val None: ConnectionVisibility = ConnectionVisibility(0)

    /** Visible to everyone */
    val Everyone: ConnectionVisibility = ConnectionVisibility(1)

    def unknown(value: Int): ConnectionVisibility = new ConnectionVisibility(value)

    def values: Seq[ConnectionVisibility] = Seq(None, Everyone)

  }
}

/** The role connection object that an application has attached to a user. */
class ApplicationRoleConnection(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The vanity name of the platform a bot has connected (max 50 characters) */
  @inline def platformName: Option[String] = selectDynamic[Option[String]]("platform_name")

  /** The username on the platform a bot has connected (max 100 characters) */
  @inline def platformUsername: Option[String] = selectDynamic[Option[String]]("platform_username")

  /**
    * Object mapping application role connection metadata keys to their
    * string-ified value (max 100 characters) for the user on the platform a bot
    * has connected
    */
  @inline def metadata: Map[String, String] = selectDynamic[Map[String, String]]("metadata")

  override def values: Seq[() => Any] = Seq(() => platformName, () => platformUsername, () => metadata)
}
object ApplicationRoleConnection extends DiscordObjectCompanion[ApplicationRoleConnection] {
  def makeRaw(json: Json, cache: Map[String, Any]): ApplicationRoleConnection =
    new ApplicationRoleConnection(json, cache)

  /**
    * @param platformName
    *   The vanity name of the platform a bot has connected (max 50 characters)
    * @param platformUsername
    *   The username on the platform a bot has connected (max 100 characters)
    * @param metadata
    *   Object mapping application role connection metadata keys to their
    *   string-ified value (max 100 characters) for the user on the platform a
    *   bot has connected
    */
  def make20(
      platformName: Option[String],
      platformUsername: Option[String],
      metadata: Map[String, String]
  ): ApplicationRoleConnection =
    makeRawFromFields("platform_name" := platformName, "platform_username" := platformUsername, "metadata" := metadata)

}

/**
  * Roles represent a set of permissions attached to a group of users. Roles
  * have names, colors, and can be "pinned" to the side bar, causing their
  * members to be listed separately. Roles can have separate permission profiles
  * for the global context (guild) and channel context. The @everyone role has
  * the same ID as the guild it belongs to.
  */
class Role(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) with UserOrRole {

  /** Role id */
  @inline def id: RoleId = selectDynamic[RoleId]("id")

  /** Role name */
  @inline def name: String = selectDynamic[String]("name")

  /** Integer representation of hexadecimal color code */
  @inline def color: Int = selectDynamic[Int]("color")

  /** If this role is pinned in the user listing */
  @inline def hoist: Boolean = selectDynamic[Boolean]("hoist")

  /** Role icon hash */
  @inline def icon: JsonOption[ImageHash] = selectDynamic[JsonOption[ImageHash]]("icon")

  /** Role unicode emoji */
  @inline def unicodeEmoji: JsonOption[String] = selectDynamic[JsonOption[String]]("unicode_emoji")

  /** Position of this role */
  @inline def position: Int = selectDynamic[Int]("position")

  /** Permission bit set */
  @inline def permissions: Permissions = selectDynamic[Permissions]("permissions")

  /** Whether this role is managed by an integration */
  @inline def managed: Boolean = selectDynamic[Boolean]("managed")

  /** Whether this role is mentionable */
  @inline def mentionable: Boolean = selectDynamic[Boolean]("mentionable")

  /** The tags this role has */
  @inline def tags: Role.RoleTag = selectDynamic[Role.RoleTag]("tags")

  /** Role flags combined as a bitfield */
  @inline def flags: Role.RoleFlags = selectDynamic[Role.RoleFlags]("flags")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => name,
    () => color,
    () => hoist,
    () => icon,
    () => unicodeEmoji,
    () => position,
    () => permissions,
    () => managed,
    () => mentionable,
    () => tags,
    () => flags
  )
}
object Role extends DiscordObjectCompanion[Role] {
  def makeRaw(json: Json, cache: Map[String, Any]): Role = new Role(json, cache)

  /**
    * @param id
    *   Role id
    * @param name
    *   Role name
    * @param color
    *   Integer representation of hexadecimal color code
    * @param hoist
    *   If this role is pinned in the user listing
    * @param icon
    *   Role icon hash
    * @param unicodeEmoji
    *   Role unicode emoji
    * @param position
    *   Position of this role
    * @param permissions
    *   Permission bit set
    * @param managed
    *   Whether this role is managed by an integration
    * @param mentionable
    *   Whether this role is mentionable
    * @param tags
    *   The tags this role has
    * @param flags
    *   Role flags combined as a bitfield
    */
  def make20(
      id: RoleId,
      name: String,
      color: Int,
      hoist: Boolean,
      icon: JsonOption[ImageHash] = JsonUndefined,
      unicodeEmoji: JsonOption[String] = JsonUndefined,
      position: Int,
      permissions: Permissions,
      managed: Boolean,
      mentionable: Boolean,
      tags: Role.RoleTag,
      flags: Role.RoleFlags
  ): Role = makeRawFromFields(
    "id"             := id,
    "name"           := name,
    "color"          := color,
    "hoist"          := hoist,
    "icon"          :=? icon,
    "unicode_emoji" :=? unicodeEmoji,
    "position"       := position,
    "permissions"    := permissions,
    "managed"        := managed,
    "mentionable"    := mentionable,
    "tags"           := tags,
    "flags"          := flags
  )

  /**
    * Tags with type null represent booleans. They will be present and set to
    * null if they are "true", and will be not present if they are "false".
    */
  class RoleTag(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The id of the bot this role belongs to */
    @inline def botId: UndefOr[UserId] = selectDynamic[UndefOr[UserId]]("bot_id")

    /** The id of the integration this role belongs to */
    @inline def integrationId: UndefOr[Snowflake[Integration]] =
      selectDynamic[UndefOr[Snowflake[Integration]]]("integration_id")

    /** Whether this is the guild's Booster role */
    @inline def premiumSubscriber: JsonOption[Unit] = selectDynamic[JsonOption[Unit]]("premium_subscriber")

    /** The id of this role's subscription sku and listing */
    @inline def subscriptionListingId: UndefOr[RawSnowflake] =
      selectDynamic[UndefOr[RawSnowflake]]("subscription_listing_id")

    /** Whether this role is available for purchase */
    @inline def availableForPurchase: JsonOption[Unit] = selectDynamic[JsonOption[Unit]]("available_for_purchase")

    /** Whether this role is a guild's linked role */
    @inline def guildConnections: JsonOption[Unit] = selectDynamic[JsonOption[Unit]]("guild_connections")

    override def values: Seq[() => Any] = Seq(
      () => botId,
      () => integrationId,
      () => premiumSubscriber,
      () => subscriptionListingId,
      () => availableForPurchase,
      () => guildConnections
    )
  }
  object RoleTag extends DiscordObjectCompanion[RoleTag] {
    def makeRaw(json: Json, cache: Map[String, Any]): RoleTag = new RoleTag(json, cache)

    /**
      * @param botId
      *   The id of the bot this role belongs to
      * @param integrationId
      *   The id of the integration this role belongs to
      * @param premiumSubscriber
      *   Whether this is the guild's Booster role
      * @param subscriptionListingId
      *   The id of this role's subscription sku and listing
      * @param availableForPurchase
      *   Whether this role is available for purchase
      * @param guildConnections
      *   Whether this role is a guild's linked role
      */
    def make20(
        botId: UndefOr[UserId] = UndefOrUndefined,
        integrationId: UndefOr[Snowflake[Integration]] = UndefOrUndefined,
        premiumSubscriber: JsonOption[Unit] = JsonUndefined,
        subscriptionListingId: UndefOr[RawSnowflake] = UndefOrUndefined,
        availableForPurchase: JsonOption[Unit] = JsonUndefined,
        guildConnections: JsonOption[Unit] = JsonUndefined
    ): RoleTag = makeRawFromFields(
      "bot_id"                  :=? botId,
      "integration_id"          :=? integrationId,
      "premium_subscriber"      :=? premiumSubscriber,
      "subscription_listing_id" :=? subscriptionListingId,
      "available_for_purchase"  :=? availableForPurchase,
      "guild_connections"       :=? guildConnections
    )

  }

  sealed case class RoleFlags private (value: Int) extends DiscordEnum[Int]
  object RoleFlags                                 extends DiscordEnumCompanion[Int, RoleFlags] {

    /** Role can be selected by members in an onboarding prompt */
    val IN_PROMPT: RoleFlags = RoleFlags(1 << 0)

    def unknown(value: Int): RoleFlags = new RoleFlags(value)

    def values: Seq[RoleFlags] = Seq(IN_PROMPT)

    implicit class RoleFlagsBitFieldOps(private val here: RoleFlags) extends AnyVal {
      def toInt: Int = here.value

      def ++(there: RoleFlags): RoleFlags = RoleFlags(here.value | there.value)

      def --(there: RoleFlags): RoleFlags = RoleFlags(here.value & ~there.value)

      def isNone: Boolean = here.value == 0
    }
  }
}

//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/OAuth2Scope.yaml

import ackcord.data.base._

sealed case class OAuth2Scope private (value: String) extends DiscordEnum[String]
object OAuth2Scope                                    extends DiscordEnumCompanion[String, OAuth2Scope] {

  /**
    * Allows your app to fetch data from a user's "Now Playing/Recently Played"
    * list — not currently available for apps
    */
  val ActivitiesRead: OAuth2Scope = OAuth2Scope("activities.read")

  /**
    * Allows your app to update a user's activity - requires Discord approval
    * (NOT REQUIRED FOR GAMESDK ACTIVITY MANAGER)
    */
  val ActivitiesWrite: OAuth2Scope = OAuth2Scope("activities.write")

  /** Allows your app to read build data for a user's applications */
  val ApplicationsBuildsRead: OAuth2Scope = OAuth2Scope("applications.builds.read")

  /**
    * Allows your app to upload/update builds for a user's applications -
    * requires Discord approval
    */
  val ApplicationsBuildsUpload: OAuth2Scope = OAuth2Scope("applications.builds.upload")

  /**
    * Allows your app to add commands to a guild - included by default with the
    * bot scope
    */
  val ApplicationsCommands: OAuth2Scope = OAuth2Scope("applications.commands")

  /**
    * Allows your app to update its commands using a Bearer token - client
    * credentials grant only
    */
  val ApplicationsCommandsUpdate: OAuth2Scope = OAuth2Scope("applications.commands.update")

  /**
    * Allows your app to update permissions for its commands in a guild a user
    * has permissions to
    */
  val ApplicationsCommandsPermissionsUpdate: OAuth2Scope = OAuth2Scope("applications.commands.permissions.update")

  /** Allows your app to read entitlements for a user's applications */
  val ApplicationsEntitlements: OAuth2Scope = OAuth2Scope("applications.entitlements")

  /**
    * Allows your app to read and update store data (SKUs, store listings,
    * achievements, etc.) for a user's applications
    */
  val ApplicationsStoreUpdate: OAuth2Scope = OAuth2Scope("applications.store.update")

  /**
    * For oauth2 bots, this puts the bot in the user's selected guild by default
    */
  val Bot: OAuth2Scope = OAuth2Scope("bot")

  /** Allows /users/@me/connections to return linked third-party accounts */
  val Connections: OAuth2Scope = OAuth2Scope("connections")

  /**
    * Allows your app to see information about the user's DMs and group DMs -
    * requires Discord approval
    */
  val DmChannelsRead: OAuth2Scope = OAuth2Scope("dm_channels.read")

  /** Enables /users/@me to return an email */
  val Email: OAuth2Scope = OAuth2Scope("email")

  /** Allows your app to join users to a group dm */
  val GdmJoin: OAuth2Scope = OAuth2Scope("gdm.join")

  /**
    * Allows /users/@me/guilds to return basic information about all of a user's
    * guilds
    */
  val Guilds: OAuth2Scope = OAuth2Scope("guilds")

  /**
    * Allows /guilds/{guild.id}/members/{user.id} to be used for joining users
    * to a guild
    */
  val GuildsJoin: OAuth2Scope = OAuth2Scope("guilds.join")

  /**
    * Allows /users/@me/guilds/{guild.id}/member to return a user's member
    * information in a guild
    */
  val GuildsMembersRead: OAuth2Scope = OAuth2Scope("guilds.members.read")

  /** Allows /users/@me without email */
  val Identify: OAuth2Scope = OAuth2Scope("identify")

  /**
    * For local rpc server api access, this allows you to read messages from all
    * client channels (otherwise restricted to channels/guilds your app creates)
    */
  val MessagesRead: OAuth2Scope = OAuth2Scope("messages.read")

  /**
    * Allows your app to know a user's friends and implicit relationships -
    * requires Discord approval
    */
  val RelationshipsRead: OAuth2Scope = OAuth2Scope("relationships.read")

  /** Allows your app to update a user's connection and metadata for the app */
  val RoleConnectionsWrite: OAuth2Scope = OAuth2Scope("role_connections.write")

  /**
    * For local rpc server access, this allows you to control a user's local
    * Discord client - requires Discord approval
    */
  val Rpc: OAuth2Scope = OAuth2Scope("rpc")

  /**
    * For local rpc server access, this allows you to update a user's activity -
    * requires Discord approval
    */
  val RpcActivitiesWrite: OAuth2Scope = OAuth2Scope("rpc.activities.write")

  /**
    * For local rpc server access, this allows you to receive notifications
    * pushed out to the user - requires Discord approval
    */
  val RpcNotificationsRead: OAuth2Scope = OAuth2Scope("rpc.notifications.read")

  /**
    * For local rpc server access, this allows you to read a user's voice
    * settings and listen for voice events - requires Discord approval
    */
  val RpcVoiceRead: OAuth2Scope = OAuth2Scope("rpc.voice.read")

  /**
    * For local rpc server access, this allows you to update a user's voice
    * settings - requires Discord approval
    */
  val RpcVoiceWrite: OAuth2Scope = OAuth2Scope("rpc.voice.write")

  /**
    * Allows your app to connect to voice on user's behalf and see all the voice
    * members - requires Discord approval
    */
  val Voice: OAuth2Scope = OAuth2Scope("voice")

  /**
    * This generates a webhook that is returned in the oauth token response for
    * authorization code grants
    */
  val WebhookIncoming: OAuth2Scope = OAuth2Scope("webhook.incoming")

  def unknown(value: String): OAuth2Scope = new OAuth2Scope(value)

  val values: Seq[OAuth2Scope] = Seq(
    ActivitiesRead,
    ActivitiesWrite,
    ApplicationsBuildsRead,
    ApplicationsBuildsUpload,
    ApplicationsCommands,
    ApplicationsCommandsUpdate,
    ApplicationsCommandsPermissionsUpdate,
    ApplicationsEntitlements,
    ApplicationsStoreUpdate,
    Bot,
    Connections,
    DmChannelsRead,
    Email,
    GdmJoin,
    Guilds,
    GuildsJoin,
    GuildsMembersRead,
    Identify,
    MessagesRead,
    RelationshipsRead,
    RoleConnectionsWrite,
    Rpc,
    RpcActivitiesWrite,
    RpcNotificationsRead,
    RpcVoiceRead,
    RpcVoiceWrite,
    Voice,
    WebhookIncoming
  )
}

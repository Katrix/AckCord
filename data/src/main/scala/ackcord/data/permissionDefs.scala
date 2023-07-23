package ackcord.data

import io.circe._

//TODO: Make a data definition. No real need for opaque types here

trait PermissionsDefs {

  /**
    * A permission to do some action. In AckCord this is represented as a value
    * class around int.
    */
  type Permissions = Permission.Permissions
}

object Permission {
  private[data] type Base
  private[data] trait Tag extends Any

  type Permissions <: Base with Tag

  private[data] def apply(bigInt: BigInt): Permissions = bigInt.asInstanceOf[Permissions]

  /** Create a permission that has all the permissions passed in. */
  def apply(permissions: Permissions*): Permissions = permissions.fold(None)(_ ++ _)

  /** Create a permission from an int. */
  def fromBigInt(bigInt: BigInt): Permissions = apply(bigInt)

  implicit val permissionCodec: Codec[Permissions] = Codec.from(
    Decoder[BigInt].emap(i => Right(Permission.fromBigInt(i))),
    Encoder[BigInt].contramap(_.toBigInt)
  )

  val CreateInstantInvite: Permissions     = Permission(0x0000000001L)
  val KickMembers: Permissions             = Permission(0x0000000002L)
  val BanMembers: Permissions              = Permission(0x0000000004L)
  val Administrator: Permissions           = Permission(0x0000000008L)
  val ManageChannels: Permissions          = Permission(0x0000000010L)
  val ManageGuild: Permissions             = Permission(0x0000000020L)
  val AddReactions: Permissions            = Permission(0x0000000040L)
  val ViewAuditLog: Permissions            = Permission(0x0000000080L)
  val PrioritySpeaker: Permissions         = Permission(0x0000000100L)
  val Stream: Permissions                  = Permission(0x0000000200L)
  val ViewChannel: Permissions             = Permission(0x0000000400L)
  val SendMessages: Permissions            = Permission(0x0000000800L)
  val SendTtsMessages: Permissions         = Permission(0x0000001000L)
  val ManageMessages: Permissions          = Permission(0x0000002000L)
  val EmbedLinks: Permissions              = Permission(0x0000004000L)
  val AttachFiles: Permissions             = Permission(0x0000008000L)
  val ReadMessageHistory: Permissions      = Permission(0x0000010000L)
  val MentionEveryone: Permissions         = Permission(0x0000020000L)
  val UseExternalEmojis: Permissions       = Permission(0x0000040000L)
  val ViewGuildInsights: Permissions       = Permission(0x0000080000L)
  val Connect: Permissions                 = Permission(0x0000100000L)
  val Speak: Permissions                   = Permission(0x0000200000L)
  val MuteMembers: Permissions             = Permission(0x0000400000L)
  val DeafenMembers: Permissions           = Permission(0x0000800000L)
  val MoveMembers: Permissions             = Permission(0x0001000000L)
  val UseVad: Permissions                  = Permission(0x0002000000L)
  val ChangeNickname: Permissions          = Permission(0x0004000000L)
  val ManageNicknames: Permissions         = Permission(0x0008000000L)
  val ManageRoles: Permissions             = Permission(0x0010000000L)
  val ManageWebhooks: Permissions          = Permission(0x0020000000L)
  val ManageEmojisAndStickers: Permissions = Permission(0x0040000000L)
  val UseApplicationCommands: Permissions  = Permission(0x0080000000L)
  val RequestToSpeak: Permissions          = Permission(0x0100000000L)
  val ManageEvents: Permissions            = Permission(0x0200000000L)
  val ManageThreads: Permissions           = Permission(0x0400000000L)
  val CreatePublicThreads: Permissions     = Permission(0x0800000000L)
  val CreatePrivateThreads: Permissions    = Permission(0x1000000000L)
  val UseExternalStickers: Permissions     = Permission(0x2000000000L)
  val SendMessagesInThreads: Permissions   = Permission(0x4000000000L)
  val UseEmbeddedActivities: Permissions   = Permission(0x8000000000L)
  val ModerateMembers: Permissions         = Permission(0x10000000000L)

  val None: Permissions = Permission(0x00000000)
  val All: Permissions = Permission(
    CreateInstantInvite,
    KickMembers,
    BanMembers,
    Administrator,
    ManageChannels,
    ManageGuild,
    AddReactions,
    ViewAuditLog,
    PrioritySpeaker,
    Stream,
    ViewChannel,
    SendMessages,
    SendTtsMessages,
    ManageMessages,
    EmbedLinks,
    AttachFiles,
    ReadMessageHistory,
    MentionEveryone,
    UseExternalEmojis,
    ViewGuildInsights,
    Connect,
    Speak,
    MuteMembers,
    DeafenMembers,
    MoveMembers,
    UseVad,
    ChangeNickname,
    ManageNicknames,
    ManageRoles,
    ManageWebhooks,
    ManageEmojisAndStickers,
    UseApplicationCommands,
    RequestToSpeak,
    ManageEvents,
    ManageThreads,
    CreatePublicThreads,
    CreatePrivateThreads,
    UseExternalStickers,
    SendMessagesInThreads,
    UseEmbeddedActivities,
    ModerateMembers
  )

  implicit class PermissionSyntax(private val permission: Permissions) extends AnyVal {

    def toBigInt: BigInt = permission.asInstanceOf[BigInt]

    /**
      * Add a permission to this permission.
      *
      * @param other
      *   The other permission.
      */
    def addPermissions(other: Permissions): Permissions = Permission(toBigInt | other.toBigInt)

    /**
      * Add a permission to this permission.
      *
      * @param other
      *   The other permission.
      */
    def ++(other: Permissions): Permissions = addPermissions(other)

    /**
      * Remove a permission from this permission.
      *
      * @param other
      *   The permission to remove.
      */
    def removePermissions(other: Permissions): Permissions = Permission(toBigInt & ~other.toBigInt)

    /**
      * Remove a permission from this permission.
      *
      * @param other
      *   The permission to remove.
      */
    def --(other: Permissions): Permissions = removePermissions(other)

    /**
      * Toggle a permission in this permission.
      *
      * @param other
      *   The permission to toggle.
      */
    def togglePermissions(other: Permissions): Permissions = Permission(toBigInt ^ other.toBigInt)

    /**
      * Check if this permission has a permission.
      *
      * @param other
      *   The permission to check against.
      */
    def hasPermissions(other: Permissions): Boolean = (toBigInt & other.toBigInt) == other.toBigInt

    /** Check if this permission grants any permissions. */
    def isNone: Boolean = toBigInt == 0
  }
}

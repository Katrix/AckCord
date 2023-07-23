package ackcord.data

import io.circe._

//TODO: Make a data definition. No real need for opaque types here

trait PermissionsDefs {

  /**
    * A permission to do some action. In AckCord this is represented as a value
    * class around int.
    */
  type Permissions = Permissions.Permissions
}

object Permissions {
  private[data] type Base
  private[data] trait Tag extends Any

  type Permissions <: Base with Tag

  private[data] def apply(bigInt: BigInt): Permissions = bigInt.asInstanceOf[Permissions]

  /** Create a permission that has all the permissions passed in. */
  def apply(permissions: Permissions*): Permissions = permissions.fold(None)(_ ++ _)

  /** Create a permission from an int. */
  def fromBigInt(bigInt: BigInt): Permissions = apply(bigInt)

  implicit val permissionCodec: Codec[Permissions] = Codec.from(
    Decoder[BigInt].emap(i => Right(Permissions.fromBigInt(i))),
    Encoder[BigInt].contramap(_.toBigInt)
  )

  val CreateInstantInvite: Permissions     = Permissions(0x0000000001L)
  val KickMembers: Permissions             = Permissions(0x0000000002L)
  val BanMembers: Permissions              = Permissions(0x0000000004L)
  val Administrator: Permissions           = Permissions(0x0000000008L)
  val ManageChannels: Permissions          = Permissions(0x0000000010L)
  val ManageGuild: Permissions             = Permissions(0x0000000020L)
  val AddReactions: Permissions            = Permissions(0x0000000040L)
  val ViewAuditLog: Permissions            = Permissions(0x0000000080L)
  val PrioritySpeaker: Permissions         = Permissions(0x0000000100L)
  val Stream: Permissions                  = Permissions(0x0000000200L)
  val ViewChannel: Permissions             = Permissions(0x0000000400L)
  val SendMessages: Permissions            = Permissions(0x0000000800L)
  val SendTtsMessages: Permissions         = Permissions(0x0000001000L)
  val ManageMessages: Permissions          = Permissions(0x0000002000L)
  val EmbedLinks: Permissions              = Permissions(0x0000004000L)
  val AttachFiles: Permissions             = Permissions(0x0000008000L)
  val ReadMessageHistory: Permissions      = Permissions(0x0000010000L)
  val MentionEveryone: Permissions         = Permissions(0x0000020000L)
  val UseExternalEmojis: Permissions       = Permissions(0x0000040000L)
  val ViewGuildInsights: Permissions       = Permissions(0x0000080000L)
  val Connect: Permissions                 = Permissions(0x0000100000L)
  val Speak: Permissions                   = Permissions(0x0000200000L)
  val MuteMembers: Permissions             = Permissions(0x0000400000L)
  val DeafenMembers: Permissions           = Permissions(0x0000800000L)
  val MoveMembers: Permissions             = Permissions(0x0001000000L)
  val UseVad: Permissions                  = Permissions(0x0002000000L)
  val ChangeNickname: Permissions          = Permissions(0x0004000000L)
  val ManageNicknames: Permissions         = Permissions(0x0008000000L)
  val ManageRoles: Permissions             = Permissions(0x0010000000L)
  val ManageWebhooks: Permissions          = Permissions(0x0020000000L)
  val ManageEmojisAndStickers: Permissions = Permissions(0x0040000000L)
  val UseApplicationCommands: Permissions  = Permissions(0x0080000000L)
  val RequestToSpeak: Permissions          = Permissions(0x0100000000L)
  val ManageEvents: Permissions            = Permissions(0x0200000000L)
  val ManageThreads: Permissions           = Permissions(0x0400000000L)
  val CreatePublicThreads: Permissions     = Permissions(0x0800000000L)
  val CreatePrivateThreads: Permissions    = Permissions(0x1000000000L)
  val UseExternalStickers: Permissions     = Permissions(0x2000000000L)
  val SendMessagesInThreads: Permissions   = Permissions(0x4000000000L)
  val UseEmbeddedActivities: Permissions   = Permissions(0x8000000000L)
  val ModerateMembers: Permissions         = Permissions(0x10000000000L)

  val None: Permissions = Permissions(0x00000000)
  val All: Permissions = Permissions(
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
    def addPermissions(other: Permissions): Permissions = Permissions(toBigInt | other.toBigInt)

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
    def removePermissions(other: Permissions): Permissions = Permissions(toBigInt & ~other.toBigInt)

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
    def togglePermissions(other: Permissions): Permissions = Permissions(toBigInt ^ other.toBigInt)

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

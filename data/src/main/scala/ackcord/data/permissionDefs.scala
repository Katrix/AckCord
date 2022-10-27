package ackcord.data

import io.circe._

import scala.language.implicitConversions

trait PermissionDefs {

  /**
    * A permission to do some action. In AckCord this is represented as a value
    * class around int.
    */
  type Permission = Permission.Permission
}

object Permission {
  private[data] type Base
  private[data] trait Tag extends Any

  type Permission <: Base with Tag

  private[data] def apply(bigInt: BigInt): Permission = bigInt.asInstanceOf[Permission]

  /** Create a permission that has all the permissions passed in. */
  def apply(permissions: Permission*): Permission = permissions.fold(None)(_ ++ _)

  /** Create a permission from an int. */
  def fromBigInt(bigInt: BigInt): Permission = apply(bigInt)

  implicit val permissionCodec: Codec[Permission] = Codec.from(
    Decoder[BigInt].emap(i => Right(Permission.fromBigInt(i))),
    Encoder[BigInt].contramap(_.toBigInt)
  )

  val CreateInstantInvite: Permission     = Permission(0x0000000001L)
  val KickMembers: Permission             = Permission(0x0000000002L)
  val BanMembers: Permission              = Permission(0x0000000004L)
  val Administrator: Permission           = Permission(0x0000000008L)
  val ManageChannels: Permission          = Permission(0x0000000010L)
  val ManageGuild: Permission             = Permission(0x0000000020L)
  val AddReactions: Permission            = Permission(0x0000000040L)
  val ViewAuditLog: Permission            = Permission(0x0000000080L)
  val PrioritySpeaker: Permission         = Permission(0x0000000100L)
  val Stream: Permission                  = Permission(0x0000000200L)
  val ViewChannel: Permission             = Permission(0x0000000400L)
  val SendMessages: Permission            = Permission(0x0000000800L)
  val SendTtsMessages: Permission         = Permission(0x0000001000L)
  val ManageMessages: Permission          = Permission(0x0000002000L)
  val EmbedLinks: Permission              = Permission(0x0000004000L)
  val AttachFiles: Permission             = Permission(0x0000008000L)
  val ReadMessageHistory: Permission      = Permission(0x0000010000L)
  val MentionEveryone: Permission         = Permission(0x0000020000L)
  val UseExternalEmojis: Permission       = Permission(0x0000040000L)
  val ViewGuildInsights: Permission       = Permission(0x0000080000L)
  val Connect: Permission                 = Permission(0x0000100000L)
  val Speak: Permission                   = Permission(0x0000200000L)
  val MuteMembers: Permission             = Permission(0x0000400000L)
  val DeafenMembers: Permission           = Permission(0x0000800000L)
  val MoveMembers: Permission             = Permission(0x0001000000L)
  val UseVad: Permission                  = Permission(0x0002000000L)
  val ChangeNickname: Permission          = Permission(0x0004000000L)
  val ManageNicknames: Permission         = Permission(0x0008000000L)
  val ManageRoles: Permission             = Permission(0x0010000000L)
  val ManageWebhooks: Permission          = Permission(0x0020000000L)
  val ManageEmojisAndStickers: Permission = Permission(0x0040000000L)
  val UseApplicationCommands: Permission  = Permission(0x0080000000L)
  val RequestToSpeak: Permission          = Permission(0x0100000000L)
  val ManageEvents: Permission            = Permission(0x0200000000L)
  val ManageThreads: Permission           = Permission(0x0400000000L)
  val CreatePublicThreads: Permission     = Permission(0x0800000000L)
  val CreatePrivateThreads: Permission    = Permission(0x1000000000L)
  val UseExternalStickers: Permission     = Permission(0x2000000000L)
  val SendMessagesInThreads: Permission   = Permission(0x4000000000L)
  val UseEmbeddedActivities: Permission   = Permission(0x8000000000L)
  val ModerateMembers: Permission         = Permission(0x10000000000L)

  val None: Permission = Permission(0x00000000)
  val All: Permission = Permission(
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

  implicit class PermissionSyntax(private val permission: Permission) extends AnyVal {

    def toBigInt: BigInt = permission.asInstanceOf[BigInt]

    /**
     * Add a permission to this permission.
     *
     * @param other
     * The other permission.
     */
    def addPermissions(other: Permission): Permission = Permission(toBigInt | other.toBigInt)

    /**
     * Add a permission to this permission.
     *
     * @param other
     * The other permission.
     */
    def ++(other: Permission): Permission = addPermissions(other)

    /**
     * Remove a permission from this permission.
     *
     * @param other
     * The permission to remove.
     */
    def removePermissions(other: Permission): Permission = Permission(toBigInt & ~other.toBigInt)

    /**
     * Remove a permission from this permission.
     *
     * @param other
     * The permission to remove.
     */
    def --(other: Permission): Permission = removePermissions(other)

    /**
     * Toggle a permission in this permission.
     *
     * @param other
     * The permission to toggle.
     */
    def togglePermissions(other: Permission): Permission = Permission(toBigInt ^ other.toBigInt)

    /**
     * Check if this permission has a permission.
     *
     * @param other
     * The permission to check against.
     */
    def hasPermissions(other: Permission): Boolean = (toBigInt & other.toBigInt) == other.toBigInt

    /** Check if this permission grants any permissions. */
    def isNone: Boolean = toBigInt == 0
  }
}

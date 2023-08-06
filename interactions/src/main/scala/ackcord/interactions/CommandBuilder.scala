package ackcord.interactions

import ackcord.data.Permissions

/** A builder of some sort of command. */
abstract class CommandBuilder[F[_], Args] {

  /** Extra info to associate with the command. */
  def extra: Map[String, String]

  /** Sets the extra info associated with the command. */
  def withExtra(extra: Map[String, String]): CommandBuilder[F, Args]

  /** The permissions a user must have to use the command by default. */
  def defaultMemberPermissions: Option[Permissions]

  /** Sets the permissions a user must have to use the command by default. */
  def withDefaultMemberPermissions(defaultMemberPermissions: Option[Permissions]): CommandBuilder[F, Args]

  /** If this command is NSFW. */
  def nsfw: Boolean

  /** Sets if this command is NSFW. */
  def withNsfw(nsfw: Boolean): CommandBuilder[F, Args]
}

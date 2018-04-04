package net.katsstuff.ackcord.http

import scala.language.higherKinds

import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data.{ChannelId, GuildId, Permission}

package object rest {

  /**
    * Check if a client has the needed permissions in a guild
    * @param guildId The guild to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsGuild[F[_]: Monad](guildId: GuildId, permissions: Permission)(
      implicit c: CacheSnapshotLike[F]
  ): F[Boolean] = {
    val res = for {
      guild         <- c.getGuild(guildId)
      botUser       <- OptionT.liftF(c.botUser)
      botUserMember <- OptionT.fromOption[F](guild.members.get(botUser.id))
    } yield botUserMember.permissions(guild).hasPermissions(permissions)

    res.getOrElse(false)
  }

  /**
    * Check if a client has the needed permissions in a channel
    * @param channelId The channel to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsChannel[F[_]: Monad](channelId: ChannelId, permissions: Permission)(
      implicit c: CacheSnapshotLike[F]
  ): F[Boolean] = {
    val opt = for {
      gChannel      <- c.getGuildChannel(channelId)
      guild         <- gChannel.guild
      botUser       <- OptionT.liftF(c.botUser)
      botUserMember <- OptionT.fromOption[F](guild.members.get(botUser.id))
      channelPerms  <- OptionT.liftF(botUserMember.channelPermissions(channelId))
    } yield channelPerms.hasPermissions(permissions)

    opt.getOrElse(false)
  }
}

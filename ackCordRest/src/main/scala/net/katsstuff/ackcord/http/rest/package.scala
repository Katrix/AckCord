package net.katsstuff.ackcord.http

import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data.{ChannelId, GuildId, Permission}

package object rest {

  /**
    * Check if a client has the needed permissions in a guild
    * @param guildId The guild to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsGuild(guildId: GuildId, permissions: Permission)(implicit c: CacheSnapshotLike): Boolean = {
    c.getGuild(guildId).forall { g =>
      g.members.get(c.botUser.id).forall { mem =>
        mem.permissions.hasPermissions(permissions)
      }
    }
  }

  /**
    * Check if a client has the needed permissions in a channel
    * @param channelId The channel to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsChannel(channelId: ChannelId, permissions: Permission)(implicit c: CacheSnapshotLike): Boolean = {
    c.getGuildChannel(channelId).forall { gChannel =>
      gChannel.guild.forall { g =>
        g.members.get(c.botUser.id).forall { mem =>
          mem.permissions.hasPermissions(permissions)
        }
      }
    }
  }
}

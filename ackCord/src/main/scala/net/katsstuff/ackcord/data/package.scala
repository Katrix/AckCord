/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.katsstuff.ackcord
import shapeless._
import shapeless.tag._

package object data {

  //Some type aliases for better documentation by the types
  type GuildId = Snowflake @@ Guild
  object GuildId {
    def apply(s: Snowflake): GuildId = {
      val t = tag[Guild](s)
      t
    }
  }
  implicit class GuildIdSyntax(private val guildId: GuildId) extends AnyVal {

    /**
      * Resolve the guild represented by this id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Guild] = c.getGuild(guildId)
  }

  type ChannelId = Snowflake @@ Channel
  object ChannelId {
    def apply(s: Snowflake): ChannelId = {
      val t = tag[Channel](s)
      t
    }
  }
  implicit class ChannelIdSyntax(private val channelId: ChannelId) extends AnyVal {

    /**
      * Resolve the channel represented by this id. If a guild id is know,
      * prefer one of the guildResolve methods instead.
      */
    def resolve(implicit c: CacheSnapshot): Option[Channel] = c.getChannel(channelId)

    /**
      * Resolve the channel represented by this id as a guild channel. If a
      * guild id is know, prefer one of the other guildResolve methods instead.
      */
    def guildResolve(implicit c: CacheSnapshot): Option[GuildChannel] = c.getGuildChannel(channelId)

    /**
      * Resolve the channel represented by this id relative to a guild id.
      */
    def guildResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[GuildChannel] =
      c.getGuildChannel(guildId, channelId)

    /**
      * Resolve the channel represented by this id as a text channel. If a
      * guild id is know, prefer the other tResolve method instead.
      */
    def tResolve(implicit c: CacheSnapshot): Option[TChannel] = c.getTChannel(channelId)

    /**
      * Resolve the channel represented by this id as a text channel relative
      * to a guild id.
      */
    def tResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[TGuildChannel] =
      c.getGuildChannel(guildId, channelId).collect { case tc: TGuildChannel => tc }

    /**
      * Resolve the channel represented by this id as a voice channel relative
      * to a guild id.
      */
    def vResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[VGuildChannel] =
      c.getGuildChannel(guildId, channelId).collect { case vc: VGuildChannel => vc }
  }

  type MessageId = Snowflake @@ Message
  object MessageId {
    def apply(s: Snowflake): MessageId = {
      val t = tag[Message](s)
      t
    }
  }
  implicit class MessageIdSyntax(private val messageId: MessageId) extends AnyVal {

    /**
      * Resolve the message represented by this id. If a channel id is known,
      * prefer the method that takes a channel id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Message] = c.getMessage(messageId)

    /**
      * Resolves the message represented by this id relative to a channel id.
      */
    def resolve(channelId: ChannelId)(implicit c: CacheSnapshot): Option[Message] = c.getMessage(channelId, messageId)
  }

  type UserId = Snowflake @@ User
  object UserId {
    def apply(s: Snowflake): UserId = {
      val t = tag[User](s)
      t
    }
  }
  implicit class UserIdSyntax(private val userId: UserId) extends AnyVal {

    /**
      * Resolve the user represented by this id.
      */
    def resolve(implicit c: CacheSnapshot): Option[User] = c.getUser(userId)

    /**
      * Resolve the guild member represented by this id.
      * @param guildId The guild to find the guild member in
      */
    def resolveMember(guildId: GuildId)(implicit c: CacheSnapshot): Option[GuildMember] =
      c.getGuild(guildId).flatMap(_.members.get(userId))
  }

  type RoleId = Snowflake @@ Role
  object RoleId {
    def apply(s: Snowflake): RoleId = {
      val t = tag[Role](s)
      t
    }
  }
  implicit class RoleIdSyntax(private val roleId: RoleId) extends AnyVal {

    /**
      * Resolve the role this id represents. If a guild id is known, prefer
      * the method that takes a guild id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Role] = c.getRole(roleId)

    /**
      * Resolve the role this id represents relative to a guild id.
      */
    def resolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[Role] =
      c.getGuild(guildId).flatMap(_.roles.get(roleId))
  }

  type UserOrRoleId = Snowflake
  object UserOrRoleId {
    def apply(s: Snowflake): UserOrRoleId = s
  }

  type EmojiId = Snowflake @@ Emoji
  object EmojiId {
    def apply(s: Snowflake): EmojiId = {
      val t = tag[Emoji](s)
      t
    }
  }
  implicit class EmojiIdSyntax(private val emojiId: EmojiId) extends AnyVal {

    /**
      * Resolve the emoji this id represents. If a guild id is known, prefer
      * the method that takes a guild id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Emoji] = c.getEmoji(emojiId)

    /**
      * Resolve the emoji this id represents relative to a guild id.
      */
    def resolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[Emoji] =
      c.getGuild(guildId).flatMap(_.emojis.get(emojiId))
  }

  type IntegrationId = Snowflake @@ Integration
  object IntegrationId {
    def apply(s: Snowflake): IntegrationId = {
      val t = tag[Integration](s)
      t
    }
  }
}

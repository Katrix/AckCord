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

  type ChannelId = Snowflake @@ Channel
  object ChannelId {
    def apply(s: Snowflake): ChannelId = {
      val t = tag[Channel](s)
      t
    }
  }

  type MessageId = Snowflake @@ Message
  object MessageId {
    def apply(s: Snowflake): MessageId = {
      val t = tag[Message](s)
      t
    }
  }

  type UserId = Snowflake @@ User
  object UserId {
    def apply(s: Snowflake): UserId = {
      val t = tag[User](s)
      t
    }
  }

  type RoleId = Snowflake @@ Role
  object RoleId {
    def apply(s: Snowflake): RoleId = {
      val t = tag[Role](s)
      t
    }
  }

  type UserOrRoleId = Snowflake
  object UserOrRoleId {
    def apply(s: Snowflake): UserOrRoleId = s
  }

  type EmojiId = Snowflake @@ GuildEmoji
  object EmojiId {
    def apply(s: Snowflake): EmojiId = {
      val t = tag[GuildEmoji](s)
      t
    }
  }

  type IntegrationId = Snowflake @@ Integration
  object IntegrationId {
    def apply(s: Snowflake): IntegrationId = {
      val t = tag[Integration](s)
      t
    }
  }
}

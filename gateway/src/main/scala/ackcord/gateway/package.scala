/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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
package ackcord

package object gateway {

  type GatewayIntents = GatewayIntents.GatewayIntents
  object GatewayIntents {
    type GatewayIntents

    private[gateway] def apply(int: Int): GatewayIntents = int.asInstanceOf[GatewayIntents]

    /**
      * Create a UserFlag that has all the flags passed in.
      */
    def apply(flags: GatewayIntents*): GatewayIntents = flags.fold(None)(_ ++ _)

    /**
      * Create a UserFlag from an int.
      */
    def fromInt(int: Int): GatewayIntents = apply(int)

    val None: GatewayIntents = GatewayIntents(0)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.GuildCreate]]
      * - [[ackcord.gateway.GatewayEvent.GuildDelete]]
      * - [[ackcord.gateway.GatewayEvent.GuildRoleCreate]]
      * - [[ackcord.gateway.GatewayEvent.GuildRoleUpdate]]
      * - [[ackcord.gateway.GatewayEvent.GuildRoleDelete]]
      * - [[ackcord.gateway.GatewayEvent.ChannelCreate]]
      * - [[ackcord.gateway.GatewayEvent.ChannelUpdate]]
      * - [[ackcord.gateway.GatewayEvent.ChannelDelete]]
      * - [[ackcord.gateway.GatewayEvent.ChannelPinsUpdate]]
      */
    val Guilds: GatewayIntents = GatewayIntents(1 << 0)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.GuildMemberAdd]]
      * - [[ackcord.gateway.GatewayEvent.GuildMemberUpdate]]
      * - [[ackcord.gateway.GatewayEvent.GuildMemberRemove]]
      */
    val GuildMembers: GatewayIntents = GatewayIntents(1 << 1)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.GuildBanAdd]]
      * - [[ackcord.gateway.GatewayEvent.GuildBanRemove]]
      */
    val GuildBans: GatewayIntents = GatewayIntents(1 << 2)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.GuildEmojisUpdate]]
      */
    val GuildEmojis: GatewayIntents = GatewayIntents(1 << 3)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.GuildIntegrationsUpdate]]
      */
    val GuildIntegrations: GatewayIntents = GatewayIntents(1 << 4)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.WebhookUpdate]]
      */
    val GuildWebhooks: GatewayIntents = GatewayIntents(1 << 5)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.InviteCreate]]
      * - [[ackcord.gateway.GatewayEvent.InviteDelete]]
      */
    val GuildInvites: GatewayIntents = GatewayIntents(1 << 6)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.VoiceStateUpdate]]
      */
    val GuildVoiceStates: GatewayIntents = GatewayIntents(1 << 7)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.PresenceUpdate]]
      */
    val GuildPresences: GatewayIntents = GatewayIntents(1 << 8)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.MessageCreate]]
      * - [[ackcord.gateway.GatewayEvent.MessageUpdate]]
      * - [[ackcord.gateway.GatewayEvent.MessageDelete]]
      */
    val GuildMessages: GatewayIntents = GatewayIntents(1 << 9)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.MessageReactionAdd]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemove]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemoveAll]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemoveEmoji]]
      */
    val GuildMessageReactions: GatewayIntents = GatewayIntents(1 << 10)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.TypingStart]]
      */
    val GuildMessageTyping: GatewayIntents = GatewayIntents(1 << 11)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.ChannelCreate]]
      * - [[ackcord.gateway.GatewayEvent.MessageCreate]]
      * - [[ackcord.gateway.GatewayEvent.MessageUpdate]]
      * - [[ackcord.gateway.GatewayEvent.MessageDelete]]
      * - [[ackcord.gateway.GatewayEvent.ChannelPinsUpdate]]
      */
    val DirectMessages: GatewayIntents = GatewayIntents(1 << 12)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.MessageReactionAdd]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemove]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemoveAll]]
      * - [[ackcord.gateway.GatewayEvent.MessageReactionRemoveEmoji]]
      */
    val DirectMessagesReactions: GatewayIntents = GatewayIntents(1 << 13)

    /**
      * Allows you to receive.
      * - [[ackcord.gateway.GatewayEvent.TypingStart]]
      */
    val DirectMessageTyping: GatewayIntents = GatewayIntents(1 << 14)

    val AllNonPrivileged: GatewayIntents = GatewayIntents(
      Guilds,
      GuildBans,
      GuildEmojis,
      GuildIntegrations,
      GuildWebhooks,
      GuildInvites,
      GuildVoiceStates,
      GuildMessages,
      GuildMessageReactions,
      GuildMessageTyping,
      DirectMessages,
      DirectMessagesReactions,
      DirectMessageTyping
    )
    val All: GatewayIntents = GatewayIntents(AllNonPrivileged, GuildMembers, GuildPresences)
  }
  implicit class GatewayIntentsSyntax(private val intents: GatewayIntents) extends AnyVal {

    def toInt: Int = intents.asInstanceOf[Int]

    /**
      * Add an intent to these intents.
      * @param other The other intent.
      */
    def ++(other: GatewayIntents): GatewayIntents = GatewayIntents(toInt | other.toInt)

    /**
      * Remove an intent from these intents.
      * @param other The intent to remove.
      */
    def --(other: GatewayIntents): GatewayIntents = GatewayIntents(toInt & ~other.toInt)

    /**
      * Check if these intents has an intent.
      * @param other The intent to check against.
      */
    def hasFlag(other: GatewayIntents): Boolean = (toInt & other.toInt) == other.toInt

    /**
      * Check if these intents is empty.
      */
    def isNone: Boolean = toInt == 0
  }
}

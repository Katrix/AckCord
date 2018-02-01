/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.benchmark

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.SplittableRandom

import scala.collection.immutable.List
import scala.collection.mutable

import net.katsstuff.ackcord.SnowflakeMap
import net.katsstuff.ackcord.data._

object GenData {

  val rand = new SplittableRandom()

  def randomUser() = User(
    username = randString(rand.nextInt(32) + 1),
    id = UserId(rand.nextLong()),
    discriminator = rand.nextInt(10000).toString,
    avatar = None,
    bot = Some(false),
    mfaEnabled = None,
    verified = None,
    email = None
  )

  def randomGuild(users: Seq[User], memberCount: Int): Guild = {
    val guildId = GuildId(rand.nextLong())
    val roles = SnowflakeMap(
      Seq.tabulate(rand.nextInt(32) + 1)(pos => randomRole(guildId, pos)).map(r => r.id -> r): _*
    )
    val categories = Seq.tabulate(rand.nextInt(8) + 1)(pos => randomCategory(guildId, pos))
    val tChannels =
      Seq.tabulate(rand.nextInt(32) + 1)(pos => randomTGuildChannel(guildId, pos, categories.map(_.id)))
    val vChannels =
      Seq.tabulate(rand.nextInt(32) + 1)(pos => randomVGuildChannel(guildId, pos, categories.map(_.id)))
    val channels   = categories ++ tChannels ++ vChannels
    val guildUsers = randomElements(users, memberCount)

    Guild(
      id = guildId,
      name = randString(rand.nextInt(32) + 1),
      icon = None,
      splash = None,
      owner = Some(false),
      ownerId = guildUsers(rand.nextInt(guildUsers.length)).id,
      permissions = None,
      region = randString(rand.nextInt(32) + 1),
      afkChannelId = None,
      afkTimeout = rand.nextInt(128),
      embedEnabled = Some(false),
      embedChannelId = None,
      verificationLevel = VerificationLevel.forId(rand.nextInt(5)).get,
      defaultMessageNotifications = NotificationLevel.forId(rand.nextInt(2)).get,
      explicitContentFilter = FilterLevel.forId(rand.nextInt(3)).get,
      roles = roles,
      emojis = SnowflakeMap(
        Seq.tabulate(rand.nextInt(32))(pos => randomEmoji(pos, roles.keys.toSeq, guildUsers)).map(e => e.id -> e): _*
      ),
      features = Seq.empty,
      mfaLevel = MFALevel.forId(rand.nextInt(2)).get,
      applicationId = None,
      widgetEnabled = Some(false),
      widgetChannelId = None,
      systemChannelId = Some(tChannels(rand.nextInt(tChannels.length)).id),
      joinedAt = randomOffsetDatetime(),
      large = memberCount > 250,
      memberCount = memberCount,
      voiceStates = SnowflakeMap.empty,
      members = SnowflakeMap(
        guildUsers.map(u => randomGuildMember(u.id, guildId, roles.keys.toSeq)).map(m => m.userId -> m): _*
      ),
      channels = SnowflakeMap(channels.map(c => c.id                            -> c): _*),
      presences = SnowflakeMap(guildUsers.map(randomPresence).map(p => p.userId -> p): _*)
    )
  }

  def randomRole(guildId: GuildId, pos: Int) = Role(
    id = RoleId(rand.nextLong()),
    guildId = guildId,
    name = randString(rand.nextInt(32) + 1),
    color = rand.nextInt(0xFFFFFF),
    hoist = rand.nextBoolean(),
    position = pos,
    permissions = Permission.fromLong(rand.nextLong()),
    managed = false,
    mentionable = rand.nextBoolean()
  )

  def randomCategory(guildId: GuildId, pos: Int) = GuildCategory(
    id = ChannelId(rand.nextLong()),
    guildId = guildId,
    name = randString(rand.nextInt(32) + 1),
    position = pos,
    permissionOverwrites = SnowflakeMap.empty,
    nsfw = rand.nextBoolean(),
    parentId = None
  )

  def randomTGuildChannel(guildId: GuildId, pos: Int, categories: Seq[ChannelId]) = TGuildChannel(
    id = ChannelId(rand.nextLong()),
    guildId = guildId,
    name = randString(rand.nextInt(32) + 1),
    position = pos,
    permissionOverwrites = SnowflakeMap.empty,
    topic = if (rand.nextBoolean()) Some(randString(rand.nextInt(256) + 1)) else None,
    lastMessageId = None,
    nsfw = rand.nextBoolean(),
    parentId =
      if (rand.nextBoolean() && categories.nonEmpty) Some(categories(rand.nextInt(categories.length))) else None,
    lastPinTimestamp = None
  )

  def randomVGuildChannel(guildId: GuildId, pos: Int, categories: Seq[ChannelId]) = VGuildChannel(
    id = ChannelId(rand.nextLong()),
    guildId = guildId,
    name = randString(rand.nextInt(32) + 1),
    position = pos,
    permissionOverwrites = SnowflakeMap.empty,
    bitrate = 96000,
    userLimit = rand.nextInt(99) + 1,
    nsfw = rand.nextBoolean(),
    parentId =
      if (rand.nextBoolean() && categories.nonEmpty) Some(categories(rand.nextInt(categories.length))) else None,
  )

  def randomMessage(channelId: ChannelId, authorId: RawSnowflake, timestamp: OffsetDateTime) = Message(
    id = MessageId(rand.nextLong()),
    channelId = channelId,
    authorId = authorId,
    isAuthorUser = true,
    content = randString(rand.nextInt(1024) + 1),
    timestamp = timestamp,
    editedTimestamp = None,
    tts = false,
    mentionEveryone = false,
    mentions = Seq.empty,
    mentionRoles = Seq.empty,
    attachment = Seq.empty,
    embeds = Seq.empty,
    reactions = Seq.empty,
    nonce = None,
    pinned = false,
    messageType = MessageType.Default
  )

  def randomEmoji(i: Int, roles: Seq[RoleId], users: Seq[User]) = Emoji(
    id = EmojiId(rand.nextLong()),
    name = randString(rand.nextInt(32) + 1),
    roles = randomElements(roles, rand.nextInt(roles.length)),
    userId = Some(users(rand.nextInt(users.length)).id),
    requireColons = true,
    managed = false,
    animated = false
  )

  def randomGuildMember(userId: UserId, guildId: GuildId, roles: Seq[RoleId]) =
    GuildMember(
      userId = userId,
      guildId = guildId,
      nick = if (rand.nextBoolean()) Some(randString(rand.nextInt(32) + 1)) else None,
      roleIds = randomElements(roles, rand.nextInt(8) + 1),
      joinedAt = randomOffsetDatetime(),
      deaf = false,
      mute = false
    )

  def randomOffsetDatetime(): OffsetDateTime =
    OffsetDateTime.of(
      2017 + rand.nextInt(4) - 2,
      rand.nextInt(12) + 1,
      rand.nextInt(27) + 1,
      rand.nextInt(24),
      rand.nextInt(60),
      rand.nextInt(60),
      rand.nextInt(999999999),
      ZoneOffset.UTC
    )

  def randomPresence(user: User) = Presence(
    userId = user.id,
    content = if (rand.nextBoolean()) Some(PresenceGame(randString(rand.nextInt(32) + 1))) else None,
    status = PresenceStatus.Online
  )

  def randString(length: Int): String = {
    def safeChar() = {
      val surrogateStart: Int = 0xD800
      val res = rand.nextInt(surrogateStart - 1) + 1
      res.toChar
    }

    List.fill(length)(safeChar()).mkString
  }

  def randomElements[A](xs: Seq[A], amount: Int): Seq[A] = {

    def randElem(xs: mutable.Buffer[A], amount: Int, acc: mutable.Buffer[A]): Seq[A] = {
      if (amount <= 0 || xs.isEmpty) acc
      else {
        val idx  = rand.nextInt(xs.size)
        val elem = xs.remove(idx)
        randElem(xs, amount - 1, acc += elem)
      }
    }

    randElem(mutable.Buffer(xs: _*), amount, mutable.Buffer.empty).toIndexedSeq
  }

  def firstRandomElements[A](xs: Seq[A], pred: A => Boolean): Option[A] = {

    def randElem(xs: mutable.Buffer[A]): Option[A] = {
      if (xs.isEmpty) None
      else {
        val idx  = rand.nextInt(xs.size)
        val elem = xs.remove(idx)
        if (pred(elem)) Some(elem)
        else randElem(xs)
      }
    }

    randElem(mutable.Buffer(xs: _*))
  }

}

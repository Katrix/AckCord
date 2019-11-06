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

package ackcord.cachehandlers

import scala.collection.mutable
import scala.reflect.ClassTag

import ackcord.SnowflakeMap
import ackcord.data._
import ackcord.data.raw._
import ackcord.gateway.GatewayEvent._
import org.slf4j.Logger

object CacheHandlers {

  //Updates

  val partialUserUpdater: CacheUpdater[PartialUser] = new CacheUpdater[PartialUser] {
    override def handle(builder: CacheSnapshotBuilder, partialUser: PartialUser, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {

      builder.getUser(partialUser.id) match {
        case Some(existingUser) =>
          registry.updateData(builder) {
            existingUser.copy(
              username = partialUser.username.getOrElse(existingUser.username),
              discriminator = partialUser.discriminator.getOrElse(existingUser.discriminator),
              avatar = partialUser.avatar.orElse(existingUser.avatar),
              bot = partialUser.bot.orElse(existingUser.bot),
              mfaEnabled = partialUser.mfaEnabled.orElse(existingUser.mfaEnabled),
              verified = partialUser.verified.orElse(existingUser.verified),
              email = partialUser.email.orElse(existingUser.email)
            )
          }

        case None =>
          //Let's try to create a user
          for {
            username      <- partialUser.username
            discriminator <- partialUser.discriminator
          } {

            registry.updateData(builder) {
              User(
                id = partialUser.id,
                username = username,
                discriminator = discriminator,
                avatar = partialUser.avatar,
                bot = partialUser.bot,
                mfaEnabled = partialUser.mfaEnabled,
                verified = partialUser.verified,
                email = partialUser.email,
                flags = partialUser.flags,
                premiumType = partialUser.premiumType
              )
            }
          }
      }
    }
  }

  val rawChannelUpdater: CacheUpdater[RawChannel] = new CacheUpdater[RawChannel] {
    override def handle(builder: CacheSnapshotBuilder, rawChannel: RawChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      rawChannel.toChannel.foreach {
        //Let's keep a match here so that we don't forget to add new types later
        case guildChannel: GuildChannel     => registry.updateData(builder)(guildChannel)
        case dmChannel: DMChannel           => registry.updateData(builder)(dmChannel)
        case groupDmChannel: GroupDMChannel => registry.updateData(builder)(groupDmChannel)
        case _: UnsupportedChannel          =>
      }
    }
  }

  val guildChannelUpdater: CacheUpdater[GuildChannel] = new CacheUpdater[GuildChannel] {
    override def handle(builder: CacheSnapshotBuilder, guildChannel: GuildChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.guildMap.get(guildChannel.guildId) match {
        case Some(guild) =>
          registry.updateData(builder) {
            guild.copy(channels = guild.channels.updated(guildChannel.id, guildChannel))
          }
        case None => log.warn(s"No guild for channel update $guildChannel")
      }
    }
  }

  val dmChannelUpdater: CacheUpdater[DMChannel] = new CacheUpdater[DMChannel] {
    override def handle(builder: CacheSnapshotBuilder, dmChannel: DMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.dmChannelMap.put(dmChannel.id, dmChannel)
  }

  val dmGroupChannelUpdater: CacheUpdater[GroupDMChannel] = new CacheUpdater[GroupDMChannel] {
    override def handle(builder: CacheSnapshotBuilder, groupDmChannel: GroupDMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.groupDmChannelMap.put(groupDmChannel.id, groupDmChannel)
  }

  val rawGuildUpdater: CacheUpdater[RawGuild] = new CacheUpdater[RawGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: RawGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val userUpdater        = registry.getUpdater[User]
      val guildUpdater       = registry.getUpdater[Guild]
      val guildMemberUpdater = guildUpdater.flatMap(_ => registry.getUpdater[GuildMember]) //We only want the member updater if we have a guild

      val rawMembers =
        obj.members
          .filter(_ => userUpdater.isDefined || guildMemberUpdater.isDefined)
          .getOrElse(Seq.empty)

      val (users, members) = (userUpdater, guildMemberUpdater) match {
        case (Some(_), Some(_)) =>
          //We use unzip here to get away with a single traversal instead of 2
          rawMembers.map { rawMember =>
            rawMember.user -> rawMember.toGuildMember(obj.id)
          }.unzip
        case (Some(_), None) => rawMembers.map(_.user) -> Nil
        case (None, Some(_)) => Nil                    -> rawMembers.map(_.toGuildMember(obj.id))
        case (None, None)    => Nil                    -> Nil
      }

      userUpdater.foreach(updater => users.foreach(user => updater.handle(builder, user, registry)))

      guildUpdater.foreach { guildUpdater =>
        val rawChannels  = obj.channels.filter(_ => registry.hasUpdater[GuildChannel]).getOrElse(Seq.empty)
        val rawPresences = obj.presences.filter(_ => registry.hasUpdater[Presence]).getOrElse(Seq.empty)

        val presences = rawPresences.map(_.toPresence).flatMap {
          case Right(value) => Seq(value)
          case Left(e) =>
            log.warn(e)
            Nil
        }
        val channels = rawChannels.flatMap(_.toGuildChannel(obj.id))

        val oldGuild = builder.getGuild(obj.id)

        //Get on Option here are because everything should be sent here
        val guild = Guild(
          id = obj.id,
          name = obj.name,
          icon = obj.icon,
          splash = obj.splash,
          isOwner = obj.owner,
          ownerId = obj.ownerId,
          permissions = obj.permissions,
          region = obj.region,
          afkChannelId = obj.afkChannelId,
          afkTimeout = obj.afkTimeout,
          embedEnabled = obj.embedEnabled,
          embedChannelId = obj.embedChannelId,
          verificationLevel = obj.verificationLevel,
          defaultMessageNotifications = obj.defaultMessageNotifications,
          explicitContentFilter = obj.explicitContentFilter,
          roles = SnowflakeMap.from(obj.roles.map(r => r.id   -> r.toRole(obj.id))),
          emojis = SnowflakeMap.from(obj.emojis.map(e => e.id -> e.toEmoji)),
          features = obj.features,
          mfaLevel = obj.mfaLevel,
          applicationId = obj.applicationId,
          widgetEnabled = obj.widgetEnabled,
          widgetChannelId = obj.widgetChannelId,
          systemChannelId = obj.systemChannelId,
          joinedAt = obj.joinedAt.orElse(oldGuild.map(_.joinedAt)).get,
          large = obj.large.orElse(oldGuild.map(_.large)).get,
          memberCount = obj.memberCount.orElse(oldGuild.map(_.memberCount)).get,
          voiceStates = obj.voiceStates
            .map(seq => SnowflakeMap.withKey(seq)(_.userId))
            .orElse(oldGuild.map(_.voiceStates))
            .get,
          members = SnowflakeMap.withKey(members)(_.userId),
          channels = SnowflakeMap.withKey(channels)(_.id),
          presences = SnowflakeMap.withKey(presences)(_.userId),
          maxPresences = obj.maxPresences.getOrElse(5000), //5000 is the default
          maxMembers = obj.maxMembers,
          vanityUrlCode = obj.vanityUrlCode,
          description = obj.description,
          banner = obj.banner,
          premiumTier = obj.premiumTier,
          premiumSubscriptionCount = obj.premiumSubscriptionCount,
          preferredLocale = obj.preferredLocale
        )

        guildUpdater.handle(builder, guild, registry)
      }
    }
  }

  val rawBanUpdater: CacheUpdater[(GuildId, RawBan)] = new CacheUpdater[(GuildId, RawBan)] {
    override def handle(builder: CacheSnapshotBuilder, topObj: (GuildId, RawBan), registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val (guildId, obj) = topObj

      if (registry.hasUpdater[Ban]) {
        builder.banMap.getOrElseUpdate(guildId, mutable.Map.empty).put(obj.user.id, obj.toBan)
      }

      registry.updateData(builder)(obj.user)
    }
  }

  val guildEmojisUpdater: CacheUpdater[GuildEmojisUpdateData] =
    new CacheUpdater[GuildEmojisUpdateData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildEmojisUpdateData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        if (registry.hasUpdater[Emoji]) {
          val GuildEmojisUpdateData(guildId, emojis) = obj
          builder.getGuild(guildId) match {
            case Some(guild) =>
              registry.updateData(builder) {
                guild.copy(emojis = SnowflakeMap.from(emojis.map(e => e.id -> e.toEmoji)))
              }
            case None => log.warn(s"Can't find guild for emojis update $obj")
          }
        }
      }
    }

  val rawGuildMemberWithGuildUpdater: CacheUpdater[RawGuildMemberWithGuild] =
    new CacheUpdater[RawGuildMemberWithGuild] {
      override def handle(builder: CacheSnapshotBuilder, obj: RawGuildMemberWithGuild, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        registry.updateData(builder)(obj.toRawGuildMember.toGuildMember(obj.guildId))
        registry.updateData(builder)(obj.user)
      }
    }

  val guildMemberUpdater: CacheUpdater[GuildMember] = new CacheUpdater[GuildMember] {
    override def handle(builder: CacheSnapshotBuilder, member: GuildMember, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.getGuild(member.guildId) match {
        case Some(guild) =>
          registry.updateData(builder) {
            guild.copy(members = guild.members.updated(member.userId, member))
          }
        case None => log.warn(s"Can't find guild for guildMember update $member")
      }
    }
  }

  val rawGuildMemberUpdater: CacheUpdater[GuildMemberUpdateData] =
    new CacheUpdater[GuildMemberUpdateData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildMemberUpdateData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        val GuildMemberUpdateData(guildId, roles, user, nick) = obj

        val eitherMember = for {
          guild       <- builder.getGuild(guildId).toRight(s"Can't find guild for user update $obj")
          guildMember <- guild.members.get(user.id).toRight(s"Can't find member for member update $obj")
        } yield guildMember

        eitherMember match {
          case Right(guildMember) =>
            registry.updateData(builder) {
              guildMember.copy(nick = nick, roleIds = roles)
            }
          case Left(e) => log.warn(e)
        }

        registry.updateData(builder)(user)
      }
    }

  val rawGuildMemberChunkUpdater: CacheUpdater[GuildMemberChunkData] =
    new CacheUpdater[GuildMemberChunkData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildMemberChunkData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        val GuildMemberChunkData(guildId, newRawMembers, _, rawPresencesOpt) = obj

        if (registry.hasUpdater[GuildMember]) {
          //We update he so that we only need one lookup for the guild and can quickly create the new member map
          builder.getGuild(guildId) match {
            case Some(guild) =>
              registry.updateData(builder) {
                guild.copy(
                  members = guild.members ++ SnowflakeMap.withKey(newRawMembers.map(_.toGuildMember(guildId)))(_.userId)
                )
              }
            case None => log.warn(s"Can't find guild for guildMember update $obj")
          }
        }

        for {
          _ <- registry.getUpdater[Presence]
          _ <- registry.getUpdater[Guild]
          guild <- builder.getGuild(guildId)
          rawPresences <- rawPresencesOpt
        } {
          val presences = rawPresences.map(_.toPresence).flatMap {
            case Right(value) => Seq(value.userId -> value)
            case Left(e) =>
              log.warn(e)
              Nil
          }

          registry.updateData(builder)(
            guild.copy(
              presences = guild.presences ++ SnowflakeMap.from(presences)
            )
          )
        }

        registry
          .getUpdater[User]
          .foreach(updater => newRawMembers.foreach(raw => updater.handle(builder, raw.user, registry)))
      }
    }

  val roleModifyDataUpdater: CacheUpdater[GuildRoleModifyData] =
    new CacheUpdater[GuildRoleModifyData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildRoleModifyData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        val GuildRoleModifyData(guildId, role) = obj
        registry.updateData(builder)(role.toRole(guildId))
      }
    }

  val roleUpdater: CacheUpdater[Role] = new CacheUpdater[Role] {
    override def handle(builder: CacheSnapshotBuilder, role: Role, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      //We need to check if this should run as the first updater always runs otherwise
      if (registry.hasUpdater[Role]) {
        builder.getGuild(role.guildId) match {
          case Some(guild) =>
            registry.updateData(builder) {
              guild.copy(roles = guild.roles.updated(role.id, role))
            }
          case None => log.warn(s"No guild found for role update $role")
        }
      }
    }
  }

  val rawMessageUpdater: CacheUpdater[RawMessage] = new CacheUpdater[RawMessage] {
    override def handle(builder: CacheSnapshotBuilder, obj: RawMessage, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val users = obj.mentions

      registry.updateData(builder)(obj.toMessage)

      registry.getUpdater[User].foreach(updater => users.foreach(user => updater.handle(builder, user, registry)))

      obj.author match {
        case user: User =>
          registry.updateData(builder)(user)

          registry.getUpdater[GuildMember].foreach { updater =>
            obj.member.foreach { rawMember =>
              updater.handle(builder, rawMember.toGuildMember(user.id, obj.guildId.get), registry)
            }
          }
        case _ => //Ignore
      }

    }
  }

  val rawPartialMessageUpdater: CacheUpdater[RawPartialMessage] =
    new CacheUpdater[RawPartialMessage] {
      override def handle(builder: CacheSnapshotBuilder, obj: RawPartialMessage, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        val newUsers = obj.mentions.getOrElse(Seq.empty)

        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.id)
        } {
          val newMessage = message.copy(
            authorId = obj.author.map(a => RawSnowflake(a.id)).getOrElse(message.authorId),
            isAuthorUser = obj.author.map(_.isUser).getOrElse(message.isAuthorUser),
            content = obj.content.getOrElse(message.content),
            timestamp = obj.timestamp.getOrElse(message.timestamp),
            editedTimestamp = obj.editedTimestamp.orElseIfUndefined(message.editedTimestamp),
            tts = obj.tts.getOrElse(message.tts),
            mentionEveryone = obj.mentionEveryone.getOrElse(message.mentionEveryone),
            mentions = obj.mentions.map(_.map(_.id)).getOrElse(message.mentions),
            mentionRoles = obj.mentionRoles.getOrElse(message.mentionRoles),
            attachment = obj.attachment.getOrElse(message.attachment),
            embeds = obj.embeds.getOrElse(message.embeds),
            reactions = obj.reactions.getOrElse(message.reactions),
            nonce = obj.nonce.orElseIfUndefined(message.nonce),
            pinned = obj.pinned.getOrElse(message.pinned)
          )
          updater.handle(builder, newMessage, registry)
        }

        registry.getUpdater[User].foreach(updater => newUsers.foreach(user => updater.handle(builder, user, registry)))
      }
    }

  val rawMessageReactionUpdater: CacheUpdater[MessageReactionData] =
    new CacheUpdater[MessageReactionData] {
      override def handle(builder: CacheSnapshotBuilder, obj: MessageReactionData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.messageId)
        } {
          val newMessage = if (message.reactions.exists(_.emoji == obj.emoji)) {
            val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
            val changed = toChange.map { emoji =>
              val isMe = if (builder.botUser.id == obj.userId) true else emoji.me
              emoji.copy(count = emoji.count + 1, me = isMe)
            }

            message.copy(reactions = toNotChange ++ changed)
          } else {
            val isMe = builder.botUser.id == obj.userId
            message.copy(reactions = Reaction(1, isMe, obj.emoji) +: message.reactions)
          }

          updater.handle(builder, newMessage, registry)
        }
      }
    }

  val lastTypedUpdater: CacheUpdater[TypingStartData] = new CacheUpdater[TypingStartData] {
    override def handle(builder: CacheSnapshotBuilder, obj: TypingStartData, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.getChannelLastTyped(obj.channelId).put(obj.userId, obj.timestamp)
  }

  val userUpdater: CacheUpdater[User] = new CacheUpdater[User] {
    override def handle(builder: CacheSnapshotBuilder, user: User, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      //We need to check if this should run as the first updater always runs otherwise
      if (registry.hasUpdater[User]) {
        builder.userMap.update(user.id, user)
      }
  }

  val guildUpdater: CacheUpdater[Guild] = new CacheUpdater[Guild] {
    override def handle(builder: CacheSnapshotBuilder, obj: Guild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.guildMap.put(obj.id, obj)

      if (builder.unavailableGuildMap.contains(obj.id)) {
        builder.unavailableGuildMap.remove(obj.id)
      }
    }
  }

  val voiceStateUpdater: CacheUpdater[VoiceState] = new CacheUpdater[VoiceState] {
    override def handle(builder: CacheSnapshotBuilder, obj: VoiceState, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val optGuild = obj.guildId
        .toRight("Can't handle VoiceState update with missing guild")
        .flatMap(builder.getGuild(_).toRight(s"No guild found for voice state $obj"))

      optGuild match {
        case Right(guild) =>
          val newVoiceStates =
            obj.channelId.fold(guild.voiceStates - obj.userId)(_ => guild.voiceStates.updated(obj.userId, obj))
          registry.updateData(builder)(guild.copy(voiceStates = newVoiceStates))
        case Left(e) => log.warn(e)
      }
    }
  }

  val messageUpdater: CacheUpdater[Message] = new CacheUpdater[Message] {
    override def handle(builder: CacheSnapshotBuilder, obj: Message, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.getChannelMessages(obj.channelId).put(obj.id, obj)
  }

  val unavailableGuildUpdater: CacheUpdater[UnavailableGuild] = new CacheUpdater[UnavailableGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: UnavailableGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.unavailableGuildMap.put(obj.id, obj)
  }

  //Deletes

  val rawChannelDeleter: CacheDeleter[RawChannel] = new CacheDeleter[RawChannel] {
    override def handle(builder: CacheSnapshotBuilder, rawChannel: RawChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      //We do the update here instead of in the respective deleter so we don't need to convert to a non raw channel
      rawChannel.`type` match {
        case ChannelType.GuildText | ChannelType.GuildVoice | ChannelType.GuildCategory | ChannelType.GuildNews |
            ChannelType.GuildStore =>
          registry.getUpdater[Guild].foreach { guildUpdater =>
            def runDelete[Tpe: ClassTag](): Unit = if (registry.hasDeleter[Tpe]) {
              rawChannel.guildId.flatMap(builder.getGuild).foreach { guild =>
                guildUpdater.handle(builder, guild.copy(channels = guild.channels - rawChannel.id), registry)
              }
            }

            rawChannel.`type` match {
              case ChannelType.GuildText     => runDelete[NormalTGuildChannel]()
              case ChannelType.GuildVoice    => runDelete[VGuildChannel]()
              case ChannelType.GuildCategory => runDelete[GuildCategory]()
              case ChannelType.GuildNews     => runDelete[NewsTGuildChannel]()
              case ChannelType.GuildStore    => runDelete[GuildStoreChannel]()
              case _                         => sys.error("impossible")
            }
          }
        case ChannelType.DM =>
          if (registry.hasDeleter[DMChannel]) {
            builder.dmChannelMap.remove(rawChannel.id)
          }

        case ChannelType.GroupDm =>
          if (registry.hasDeleter[GroupDMChannel]) {
            builder.groupDmChannelMap.remove(rawChannel.id)
          }
        case ChannelType.LFG => //We do nothing here for now
      }
    }
  }

  val guildDeleter: CacheDeleter[UnavailableGuild] = new CacheDeleter[UnavailableGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: UnavailableGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.guildMap.remove(obj.id)

      if (obj.unavailable) {
        registry.updateData(builder)(obj)
      }
    }
  }

  val rawBanDeleter: CacheDeleter[UserWithGuildId] = new CacheDeleter[UserWithGuildId] {
    override def handle(builder: CacheSnapshotBuilder, topObj: UserWithGuildId, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val UserWithGuildId(guildId, user) = topObj

      if (registry.hasDeleter[Ban]) {
        builder.banMap.get(guildId).foreach(_.remove(user.id))
      }

      registry.updateData(builder)(user)
    }
  }

  val rawGuildMemberDeleter: CacheDeleter[GuildMemberRemoveData] =
    new CacheDeleter[GuildMemberRemoveData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildMemberRemoveData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        if (registry.hasDeleter[GuildMember]) {
          val GuildMemberRemoveData(guildId, user) = obj
          builder.getGuild(guildId) match {
            case Some(guild) =>
              registry.updateData(builder)(guild.copy(members = guild.members - user.id))
            case None => log.warn(s"Couldn't get guild for member delete $obj")
          }
        }
      }
    }

  val roleDeleteDataDeleter: CacheDeleter[GuildRoleDeleteData] =
    new CacheDeleter[GuildRoleDeleteData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildRoleDeleteData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        if (registry.hasDeleter[Role]) {
          val GuildRoleDeleteData(guildId, roleId) = obj
          builder.getGuild(guildId) match {
            case Some(guild) => registry.updateData(builder)(guild.copy(roles = guild.roles - roleId))
            case None        => log.warn(s"Couldn't get guild for member delete $obj")
          }
        }
      }
    }

  val rawMessageDeleter: CacheDeleter[MessageDeleteData] =
    new CacheDeleter[MessageDeleteData] {
      override def handle(builder: CacheSnapshotBuilder, obj: MessageDeleteData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = if (registry.hasDeleter[Message]) {
        val MessageDeleteData(id, channelId, _) = obj
        builder.messageMap.get(channelId).foreach(_.remove(id))
      }
    }

  val rawMessageBulkDeleter: CacheDeleter[MessageDeleteBulkData] =
    new CacheDeleter[MessageDeleteBulkData] {
      override def handle(builder: CacheSnapshotBuilder, obj: MessageDeleteBulkData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit =
        if (registry.hasDeleter[Message]) {
          val MessageDeleteBulkData(ids, channelId, _) = obj
          builder.messageMap.get(channelId).foreach(_ --= ids)
        }

    }

  val rawMessageReactionDeleter: CacheDeleter[MessageReactionData] =
    new CacheDeleter[MessageReactionData] {
      override def handle(builder: CacheSnapshotBuilder, obj: MessageReactionData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.messageId)
        } {
          val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
          val changed = toChange.map { emoji =>
            val isMe = if (builder.botUser.id == obj.userId) false else emoji.me
            emoji.copy(count = emoji.count - 1, me = isMe)
          }

          val newMessage = message.copy(reactions = toNotChange ++ changed)
          updater.handle(builder, newMessage, registry)
        }
      }
    }

  val rawMessageReactionAllDeleter: CacheDeleter[MessageReactionRemoveAllData] =
    new CacheDeleter[MessageReactionRemoveAllData] {
      override def handle(
          builder: CacheSnapshotBuilder,
          obj: MessageReactionRemoveAllData,
          registry: CacheTypeRegistry
      )(
          implicit log: Logger
      ): Unit = {
        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.messageId)
        } {
          updater.handle(builder, message.copy(reactions = Nil), registry)
        }
      }
    }

  val guildChannelDeleter: CacheDeleter[GuildChannel] = new CacheDeleter[GuildChannel] {
    override def handle(builder: CacheSnapshotBuilder, obj: GuildChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder
        .getGuild(obj.guildId)
        .foreach(guild => registry.updateData(builder)(guild.copy(channels = guild.channels - obj.id)))
  }

  val dmChannelDeleter: CacheDeleter[DMChannel] = new CacheDeleter[DMChannel] {
    override def handle(builder: CacheSnapshotBuilder, obj: DMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.dmChannelMap.remove(obj.id)
  }

  val groupDmChannelDeleter: CacheDeleter[GroupDMChannel] = new CacheDeleter[GroupDMChannel] {
    override def handle(builder: CacheSnapshotBuilder, obj: GroupDMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.groupDmChannelMap.remove(obj.id)
  }

  val guildMemberDeleter: CacheDeleter[GuildMember] = new CacheDeleter[GuildMember] {
    override def handle(builder: CacheSnapshotBuilder, obj: GuildMember, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder
        .getGuild(obj.guildId)
        .foreach(guild => registry.updateData(builder)(guild.copy(members = guild.members - obj.userId)))
  }

  val roleDeleter: CacheDeleter[Role] = new CacheDeleter[Role] {
    override def handle(builder: CacheSnapshotBuilder, obj: Role, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder
        .getGuild(obj.guildId)
        .foreach(guild => registry.updateData(builder)(guild.copy(roles = guild.roles - obj.id)))
  }

  val messageDeleter: CacheDeleter[Message] = new CacheDeleter[Message] {
    override def handle(builder: CacheSnapshotBuilder, obj: Message, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.getChannelMessages(obj.channelId).remove(obj.id)
  }
}

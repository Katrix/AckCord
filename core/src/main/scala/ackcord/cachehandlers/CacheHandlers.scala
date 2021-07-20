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
              system = partialUser.system.orElse(existingUser.system),
              mfaEnabled = partialUser.mfaEnabled.orElse(existingUser.mfaEnabled),
              locale = partialUser.locale.orElse(existingUser.locale),
              verified = partialUser.verified.orElse(existingUser.verified),
              email = partialUser.email.orElse(existingUser.email),
              flags = partialUser.flags.orElse(existingUser.flags),
              premiumType = partialUser.premiumType.orElse(existingUser.premiumType),
              publicFlags = partialUser.publicFlags.orElse(existingUser.publicFlags)
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
                system = partialUser.system,
                mfaEnabled = partialUser.mfaEnabled,
                locale = partialUser.locale,
                verified = partialUser.verified,
                email = partialUser.email,
                flags = partialUser.flags,
                premiumType = partialUser.premiumType,
                publicFlags = partialUser.publicFlags
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
    ): Unit =
      builder.dmChannelMap = builder.dmChannelMap.updated(dmChannel.id, dmChannel)
  }

  val dmGroupChannelUpdater: CacheUpdater[GroupDMChannel] = new CacheUpdater[GroupDMChannel] {
    override def handle(builder: CacheSnapshotBuilder, groupDmChannel: GroupDMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.groupDmChannelMap = builder.groupDmChannelMap.updated(groupDmChannel.id, groupDmChannel)
  }

  val rawGuildUpdater: CacheUpdater[RawGuild] = new CacheUpdater[RawGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: RawGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      val userUpdater  = registry.getUpdater[User]
      val guildUpdater = registry.getUpdater[Guild]
      val guildMemberUpdater =
        guildUpdater.flatMap(_ => registry.getUpdater[GuildMember]) //We only want the member updater if we have a guild

      val rawMembers =
        obj.members
          .filter(_ => userUpdater.isDefined || guildMemberUpdater.isDefined)
          .getOrElse(Seq.empty)

      val (users, members) = (userUpdater, guildMemberUpdater) match {
        case (Some(_), Some(_)) =>
          //We use unzip here to get away with a single traversal instead of 2
          rawMembers.map(rawMember => rawMember.user -> rawMember.toGuildMember(obj.id)).unzip
        case (Some(_), None) => rawMembers.map(_.user) -> Nil
        case (None, Some(_)) => Nil                    -> rawMembers.map(_.toGuildMember(obj.id))
        case (None, None)    => Nil                    -> Nil
      }

      userUpdater.foreach(updater => users.foreach(user => updater.handle(builder, user, registry)))

      guildUpdater.foreach { guildUpdater =>
        val rawChannels  = obj.channels.filter(_ => registry.hasUpdater[GuildChannel]).getOrElse(Seq.empty)
        val rawPresences = obj.presences.filter(_ => registry.hasUpdater[Presence]).getOrElse(Seq.empty)

        val presences = rawPresences.map(_.toPresence)
        val channels  = rawChannels.flatMap(_.toGuildChannel(obj.id))

        val oldGuild = builder.getGuild(obj.id)

        //Get on Option here are because everything should be sent here
        val guild = Guild(
          id = obj.id,
          name = obj.name,
          icon = obj.icon,
          iconHash = obj.iconHash,
          splash = obj.splash,
          discoverySplash = obj.discoverySplash,
          isOwner = obj.owner,
          ownerId = obj.ownerId,
          permissions = obj.permissions,
          afkChannelId = obj.afkChannelId,
          afkTimeout = obj.afkTimeout,
          verificationLevel = obj.verificationLevel,
          defaultMessageNotifications = obj.defaultMessageNotifications,
          explicitContentFilter = obj.explicitContentFilter,
          roles = SnowflakeMap.from(obj.roles.map(r => r.id -> r.toRole(obj.id))),
          emojis = SnowflakeMap.from(obj.emojis.map(e => e.id -> e.toEmoji)),
          features = obj.features,
          mfaLevel = obj.mfaLevel,
          applicationId = obj.applicationId,
          widgetEnabled = obj.widgetEnabled,
          widgetChannelId = obj.widgetChannelId,
          systemChannelId = obj.systemChannelId,
          systemChannelFlags = obj.systemChannelFlags,
          rulesChannelId = obj.rulesChannelId,
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
          maxPresences = obj.maxPresences,
          maxMembers = obj.maxMembers,
          vanityUrlCode = obj.vanityUrlCode,
          description = obj.description,
          banner = obj.banner,
          premiumTier = obj.premiumTier,
          premiumSubscriptionCount = obj.premiumSubscriptionCount,
          preferredLocale = obj.preferredLocale,
          publicUpdatesChannelId = obj.publicUpdatesChannelId,
          maxVideoChannelUsers = obj.maxVideoChannelUsers,
          approximateMemberCount = obj.approximateMemberCount,
          approximatePresenceCount = obj.approximatePresenceCount,
          welcomeScreen = obj.welcomeScreen,
          nsfwLevel = obj.nsfwLevel,
          stageInstances = obj.stageInstances
            .map(seq => SnowflakeMap.withKey(seq)(_.id))
            .orElse(oldGuild.map(_.stageInstances))
            .getOrElse(SnowflakeMap.empty)
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
        builder.banMap = builder.banMap.updated(guildId, builder.getGuildBans(guildId).updated(obj.user.id, obj.toBan))
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
        val GuildMemberUpdateData(guildId, roles, user, nick, joinedAt, premiumSince, deaf, mute, pending) = obj

        val exisitingMember = for {
          guild       <- builder.getGuild(guildId)
          guildMember <- guild.members.get(user.id)
        } yield guildMember.copy(
          nick = nick,
          roleIds = roles,
          joinedAt = joinedAt,
          premiumSince = premiumSince,
          deaf = deaf.getOrElse(false),
          mute = mute.getOrElse(false)
        )

        registry.updateData(builder)(
          exisitingMember.getOrElse(
            GuildMember(user.id, guildId, nick, roles, joinedAt, None, deaf = false, mute = false, None)
          )
        )

        registry.updateData(builder)(user)
      }
    }

  val rawGuildMemberChunkUpdater: CacheUpdater[GuildMemberChunkData] =
    new CacheUpdater[GuildMemberChunkData] {
      override def handle(builder: CacheSnapshotBuilder, obj: GuildMemberChunkData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit = {
        val GuildMemberChunkData(guildId, newRawMembers, _, _, _, rawPresencesOpt, _) = obj

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
          _            <- registry.getUpdater[Presence]
          _            <- registry.getUpdater[Guild]
          guild        <- builder.getGuild(guildId)
          rawPresences <- rawPresencesOpt
        } {
          val presences = rawPresences.map(_.toPresence).map(p => p.userId -> p)

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

        val memberHandler = registry.getUpdater[GuildMember]

        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.id)
        } {
          //No copy so we make sure we get all the fields
          val newMessage = message match {
            case message: SparseMessage =>
              SparseMessage(
                id = obj.id,
                channelId = obj.channelId,
                authorId = message.authorId,
                isAuthorUser = message.isAuthorUser,
                authorUsername = obj.author.map(_.username).getOrElse(message.authorUsername),
                content = obj.content.getOrElse(message.content),
                timestamp = obj.timestamp.getOrElse(message.timestamp),
                editedTimestamp = obj.editedTimestamp.orElseIfUndefined(message.editedTimestamp),
                tts = obj.tts.getOrElse(message.tts),
                mentionEveryone = obj.mentionEveryone.getOrElse(message.mentionEveryone),
                mentions = obj.mentions.map(_.map(_.id)).getOrElse(message.mentions),
                mentionChannels = obj.mentionChannels.getOrElse(message.mentionChannels),
                attachment = obj.attachment.getOrElse(message.attachment),
                embeds = obj.embeds.getOrElse(message.embeds),
                reactions = obj.reactions.getOrElse(message.reactions),
                nonce = obj.nonce.map(_.fold(_.toString, identity)).orElseIfUndefined(message.nonce),
                pinned = obj.pinned.getOrElse(message.pinned),
                messageType = obj.`type`.getOrElse(message.messageType),
                activity = obj.activity.map(_.toMessageActivity).orElseIfUndefined(message.activity),
                application = obj.application.orElseIfUndefined(message.application),
                applicationId = obj.applicationId.orElseIfUndefined(message.applicationId),
                messageReference = obj.messageReference.orElseIfUndefined(message.messageReference),
                flags = obj.flags.orElseIfUndefined(message.flags),
                stickers = obj.stickers.orElseIfUndefined(message.stickers),
                stickerItems = obj.stickerItems.orElseIfUndefined(message.stickerItems),
                referencedMessage = message.referencedMessage, //I'm lazy
                interaction = obj.interaction.orElseIfUndefined(message.interaction),
                components = obj.components.orElseIfUndefined(Some(message.components)).toSeq.flatten
              )
            case message: GuildGatewayMessage =>
              val member = obj.member.map(_.toGuildMember(UserId(message.authorId), message.guildId))
              memberHandler.zip(member.toOption).foreach(t => t._1.handle(builder, t._2, registry))

              GuildGatewayMessage(
                id = obj.id,
                channelId = message.channelId,
                guildId = message.guildId,
                authorId = message.authorId,
                isAuthorUser = message.isAuthorUser,
                authorUsername = obj.author.map(_.username).getOrElse(message.authorUsername),
                member = member.orElseIfUndefined(message.member),
                content = obj.content.getOrElse(message.content),
                timestamp = obj.timestamp.getOrElse(message.timestamp),
                editedTimestamp = obj.editedTimestamp.orElseIfUndefined(message.editedTimestamp),
                tts = obj.tts.getOrElse(message.tts),
                mentionEveryone = obj.mentionEveryone.getOrElse(message.mentionEveryone),
                mentions = obj.mentions.map(_.map(_.id)).getOrElse(message.mentions),
                mentionRoles = obj.mentionRoles.getOrElse(message.mentionRoles),
                mentionChannels = obj.mentionChannels.getOrElse(message.mentionChannels),
                attachment = obj.attachment.getOrElse(message.attachment),
                embeds = obj.embeds.getOrElse(message.embeds),
                reactions = obj.reactions.getOrElse(message.reactions),
                nonce = obj.nonce.map(_.fold(_.toString, identity)).orElseIfUndefined(message.nonce),
                pinned = obj.pinned.getOrElse(message.pinned),
                messageType = obj.`type`.getOrElse(message.messageType),
                activity = obj.activity.map(_.toMessageActivity).orElseIfUndefined(message.activity),
                application = obj.application.orElseIfUndefined(message.application),
                applicationId = obj.applicationId.orElseIfUndefined(message.applicationId),
                messageReference = obj.messageReference.orElseIfUndefined(message.messageReference),
                flags = obj.flags.orElseIfUndefined(message.flags),
                stickers = obj.stickers.orElseIfUndefined(message.stickers),
                stickerItems = obj.stickerItems.orElseIfUndefined(message.stickerItems),
                referencedMessage = message.referencedMessage, //I'm lazy
                interaction = obj.interaction.orElseIfUndefined(message.interaction),
                components = obj.components.orElseIfUndefined(Some(message.components)).toSeq.flatten
              )
          }

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

            message.withReactions(toNotChange ++ changed)
          } else {
            val isMe = builder.botUser.id == obj.userId
            message.withReactions(Reaction(1, isMe, obj.emoji) +: message.reactions)
          }

          updater.handle(builder, newMessage, registry)
        }

        registry.getUpdater[GuildMember].foreach { updater =>
          obj.member.foreach { rawMember =>
            updater.handle(builder, rawMember.toGuildMember(obj.guildId.get), registry)
          }
        }
        registry.getUpdater[User].foreach { updater =>
          obj.member.foreach(rawMember => updater.handle(builder, rawMember.user, registry))
        }
      }
    }

  val lastTypedUpdater: CacheUpdater[TypingStartData] = new CacheUpdater[TypingStartData] {
    override def handle(builder: CacheSnapshotBuilder, obj: TypingStartData, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.lastTypedMap = builder.lastTypedMap.updated(
        obj.channelId,
        builder.getChannelLastTyped(obj.channelId).updated(obj.userId, obj.timestamp)
      )
      registry.getUpdater[GuildMember].foreach { updater =>
        obj.member.foreach(rawMember => updater.handle(builder, rawMember.toGuildMember(obj.guildId.get), registry))
      }
      registry.getUpdater[User].foreach { updater =>
        obj.member.foreach(rawMember => updater.handle(builder, rawMember.user, registry))
      }
    }
  }

  val userUpdater: CacheUpdater[User] = new CacheUpdater[User] {
    override def handle(builder: CacheSnapshotBuilder, user: User, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      //We need to check if this should run as the first updater always runs otherwise
      if (registry.hasUpdater[User]) {
        builder.userMap = builder.userMap.updated(user.id, user)
      }
  }

  val guildUpdater: CacheUpdater[Guild] = new CacheUpdater[Guild] {
    override def handle(builder: CacheSnapshotBuilder, obj: Guild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.guildMap = builder.guildMap.updated(obj.id, obj)

      if (builder.unavailableGuildMap.contains(obj.id)) {
        builder.unavailableGuildMap = builder.unavailableGuildMap - obj.id
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
          val newMembers =
            obj.member
              .filter(_ => registry.hasUpdater[RawGuildMemberWithGuild])
              .fold(guild.members)(member => guild.members.updated(obj.userId, member.toGuildMember(guild.id)))

          registry.updateData(builder)(guild.copy(voiceStates = newVoiceStates, members = newMembers))

        case Left(e) => log.warn(e)
      }
    }
  }

  val messageUpdater: CacheUpdater[Message] = new CacheUpdater[Message] {
    override def handle(builder: CacheSnapshotBuilder, obj: Message, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.messageMap =
        builder.messageMap.updated(obj.channelId, builder.getChannelMessages(obj.channelId).updated(obj.id, obj))
  }

  val unavailableGuildUpdater: CacheUpdater[UnavailableGuild] = new CacheUpdater[UnavailableGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: UnavailableGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.unavailableGuildMap = builder.unavailableGuildMap.updated(obj.id, obj)
  }

  val stageInstanceUpdater: CacheUpdater[StageInstance] = new CacheUpdater[StageInstance] {
    override def handle(builder: CacheSnapshotBuilder, obj: StageInstance, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.getGuild(obj.guildId).foreach { guild =>
        registry.updateData(builder)(guild.copy(stageInstances = guild.stageInstances.updated(obj.id, obj)))
      }
  }

  //Deletes

  val rawChannelDeleter: CacheDeleter[RawChannel] = new CacheDeleter[RawChannel] {
    override def handle(builder: CacheSnapshotBuilder, rawChannel: RawChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      //We do the update here instead of in the respective deleter so we don't need to convert to a non raw channel
      rawChannel.`type` match {
        case ChannelType.GuildText | ChannelType.GuildVoice | ChannelType.GuildStageVoice | ChannelType.GuildCategory |
            ChannelType.GuildNews | ChannelType.GuildStore =>
          registry.getUpdater[Guild].foreach { guildUpdater =>
            def runDelete[Tpe: ClassTag](): Unit = if (registry.hasDeleter[Tpe]) {
              rawChannel.guildId.flatMap(builder.getGuild).foreach { guild =>
                guildUpdater.handle(
                  builder,
                  guild.copy(channels = guild.channels - rawChannel.id.asChannelId[GuildChannel]),
                  registry
                )
              }
            }

            rawChannel.`type` match {
              case ChannelType.GuildText       => runDelete[NormalTextGuildChannel]()
              case ChannelType.GuildVoice      => runDelete[NormalVoiceGuildChannel]()
              case ChannelType.GuildStageVoice => runDelete[StageGuildChannel]()
              case ChannelType.GuildCategory   => runDelete[GuildCategory]()
              case ChannelType.GuildNews       => runDelete[NewsTextGuildChannel]()
              case ChannelType.GuildStore      => runDelete[GuildStoreChannel]()
              case _                           => sys.error("impossible")
            }
          }
        case ChannelType.DM =>
          if (registry.hasDeleter[DMChannel]) {
            builder.dmChannelMap = builder.dmChannelMap - rawChannel.id.asChannelId[DMChannel]
          }

        case ChannelType.GroupDm =>
          if (registry.hasDeleter[GroupDMChannel]) {
            builder.groupDmChannelMap = builder.groupDmChannelMap - rawChannel.id.asChannelId[GroupDMChannel]
          }
        case ChannelType.Unknown(_) =>
      }
    }
  }

  val guildDeleter: CacheDeleter[UnavailableGuild] = new CacheDeleter[UnavailableGuild] {
    override def handle(builder: CacheSnapshotBuilder, obj: UnavailableGuild, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = {
      builder.guildMap = builder.guildMap - obj.id

      if (obj.unavailable.getOrElse(false)) {
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
        builder.banMap.get(guildId).foreach(map => builder.banMap.updated(guildId, map - user.id))
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
        builder.messageMap.get(channelId).foreach(map => builder.messageMap.updated(channelId, map - id))
      }
    }

  val rawMessageBulkDeleter: CacheDeleter[MessageDeleteBulkData] =
    new CacheDeleter[MessageDeleteBulkData] {
      override def handle(builder: CacheSnapshotBuilder, obj: MessageDeleteBulkData, registry: CacheTypeRegistry)(
          implicit log: Logger
      ): Unit =
        if (registry.hasDeleter[Message]) {
          val MessageDeleteBulkData(ids, channelId, _) = obj
          builder.messageMap.get(channelId).foreach(map => builder.messageMap.updated(channelId, map -- ids))
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

          val newMessage = message.withReactions(toNotChange ++ changed)
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
          updater.handle(builder, message.withReactions(Nil), registry)
        }
      }
    }

  val rawMessageReactionEmojiDeleter: CacheDeleter[MessageReactionRemoveEmojiData] =
    new CacheDeleter[MessageReactionRemoveEmojiData] {
      override def handle(
          builder: CacheSnapshotBuilder,
          obj: MessageReactionRemoveEmojiData,
          registry: CacheTypeRegistry
      )(
          implicit log: Logger
      ): Unit = {
        for {
          updater <- registry.getUpdater[Message]
          message <- builder.getMessage(obj.channelId, obj.messageId)
        } {
          updater.handle(builder, message.withReactions(message.reactions.filter(_.emoji != obj.emoji)), registry)
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
      builder.dmChannelMap = builder.dmChannelMap - obj.id
  }

  val groupDmChannelDeleter: CacheDeleter[GroupDMChannel] = new CacheDeleter[GroupDMChannel] {
    override def handle(builder: CacheSnapshotBuilder, obj: GroupDMChannel, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit = builder.groupDmChannelMap = builder.groupDmChannelMap - obj.id
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
    ): Unit = builder.messageMap.updated(obj.channelId, builder.getChannelMessages(obj.channelId) - obj.id)
  }

  implicit val stageInstanceDeleter: CacheDeleter[StageInstance] = new CacheDeleter[StageInstance] {
    override def handle(builder: CacheSnapshotBuilder, obj: StageInstance, registry: CacheTypeRegistry)(
        implicit log: Logger
    ): Unit =
      builder.getGuild(obj.guildId).foreach { guild =>
        registry.updateData(builder)(guild.copy(stageInstances = guild.stageInstances - obj.id))
      }
  }
}

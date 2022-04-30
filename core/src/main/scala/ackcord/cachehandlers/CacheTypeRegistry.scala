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

import ackcord.data._
import ackcord.data.raw.{PartialUser, RawChannel, RawGuild, RawThreadMember}
import ackcord.gateway.GatewayEvent.RawGuildMemberWithGuild
import org.slf4j.LoggerFactory

class CacheTypeRegistry(
    val updateHandlers: Map[Class[_], CacheUpdater[_]],
    val deleteHandlers: Map[Class[_], CacheDeleter[_]]
) {

  private val log = LoggerFactory.getLogger(getClass)

  private def handleWithData[D: ClassTag, HandlerTpe[-A] <: CacheHandler[A]](
      handlers: Map[Class[_], HandlerTpe[_]],
      tpe: String,
      data: => D,
      builder: CacheSnapshotBuilder
  ): Unit = getWithData[D, HandlerTpe](tpe, handlers).foreach(_.handle(builder, data, this))

  def updateData[D: ClassTag](builder: CacheSnapshotBuilder)(data: => D): Unit =
    handleWithData(updateHandlers, "updater", data, builder)

  def deleteData[D: ClassTag](builder: CacheSnapshotBuilder)(data: => D): Unit =
    handleWithData(deleteHandlers, "deleter", data, builder)

  private def getWithData[D, HandlerTpe[-A] <: CacheHandler[A]](
      tpe: String,
      handlers: Map[Class[_], HandlerTpe[_]]
  )(implicit tag: ClassTag[D]): Option[HandlerTpe[D]] = {
    val res = handlers
      .get(tag.runtimeClass)
      .asInstanceOf[Option[HandlerTpe[D]]]
      .orElse(handlers.find(_._1.isAssignableFrom(tag.runtimeClass)).map(_._2.asInstanceOf[HandlerTpe[D]]))

    if (res.isEmpty) {
      log.debug(s"$tpe not found", new Exception(s"No $tpe found for ${tag.runtimeClass}"))
    }

    res.filter(!_.ignore)
  }

  def getUpdater[D: ClassTag]: Option[CacheUpdater[D]] =
    getWithData("updater", updateHandlers)

  def getDeleter[D: ClassTag]: Option[CacheDeleter[D]] =
    getWithData("deleter", deleteHandlers)

  def hasUpdater[D: ClassTag]: Boolean =
    getUpdater.isDefined

  def hasDeleter[D: ClassTag]: Boolean =
    getDeleter.isDefined
}
object CacheTypeRegistry {

  private val noPresencesBansEmojiStickerUpdaters: Map[Class[_], CacheUpdater[_]] = Map(
    classOf[PartialUser]             -> CacheHandlers.partialUserUpdater,
    classOf[Guild]                   -> CacheHandlers.guildUpdater,
    classOf[GuildMember]             -> CacheHandlers.guildMemberUpdater,
    classOf[RawGuildMemberWithGuild] -> CacheHandlers.rawGuildMemberWithGuildUpdater,
    classOf[ThreadGuildChannel]      -> CacheHandlers.threadChannelUpdater,
    classOf[GuildChannel]            -> CacheHandlers.guildChannelUpdater,
    classOf[DMChannel]               -> CacheHandlers.dmChannelUpdater,
    classOf[GroupDMChannel]          -> CacheHandlers.dmGroupChannelUpdater,
    classOf[RawChannel]              -> CacheHandlers.rawChannelUpdater,
    classOf[RawGuild]                -> CacheHandlers.rawGuildUpdater,
    classOf[RawThreadMember]         -> CacheHandlers.rawThreadMemberUpdater,
    classOf[User]                    -> CacheHandlers.userUpdater,
    classOf[UnavailableGuild]        -> CacheHandlers.unavailableGuildUpdater,
    classOf[Message]                 -> CacheHandlers.messageUpdater,
    classOf[Role]                    -> CacheHandlers.roleUpdater,
    classOf[StageInstance]           -> CacheHandlers.stageInstanceUpdater,
    classOf[Ban]                     -> CacheUpdater.dummy[Ban](shouldBeIgnored = true),
    classOf[Emoji]                   -> CacheUpdater.dummy[Emoji](shouldBeIgnored = true),
    classOf[Presence]                -> CacheUpdater.dummy[Presence](shouldBeIgnored = true)
  )

  private val noPresencesUpdaters: Map[Class[_], CacheUpdater[_]] = noPresencesBansEmojiStickerUpdaters ++ Map(
    classOf[Ban]     -> CacheUpdater.dummy[Ban](shouldBeIgnored = false),
    classOf[Emoji]   -> CacheUpdater.dummy[Emoji](shouldBeIgnored = false),
    classOf[Sticker] -> CacheUpdater.dummy[Sticker](shouldBeIgnored = false)
  )

  private val allUpdaters: Map[Class[_], CacheUpdater[_]] =
    noPresencesUpdaters + (classOf[Presence] -> CacheUpdater.dummy[Presence](shouldBeIgnored = false))

  private val noBanDeleters: Map[Class[_], CacheDeleter[_]] = Map(
    classOf[GuildChannel]   -> CacheHandlers.guildChannelDeleter,
    classOf[DMChannel]      -> CacheHandlers.dmChannelDeleter,
    classOf[GroupDMChannel] -> CacheHandlers.groupDmChannelDeleter,
    classOf[RawChannel]     -> CacheHandlers.rawChannelDeleter,
    classOf[GuildMember]    -> CacheHandlers.guildMemberDeleter,
    classOf[Role]           -> CacheHandlers.roleDeleter,
    classOf[Message]        -> CacheHandlers.messageDeleter,
    classOf[StageInstance]  -> CacheHandlers.stageInstanceDeleter,
    classOf[Ban]            -> CacheDeleter.dummy[Ban](shouldBeIgnored = true)
  )

  private val allDeleters: Map[Class[_], CacheDeleter[_]] =
    noBanDeleters + (classOf[Ban] -> CacheDeleter.dummy[Ban](shouldBeIgnored = false))

  def default = new CacheTypeRegistry(allUpdaters, allDeleters)

  def noPresences = new CacheTypeRegistry(noPresencesUpdaters, allDeleters)

  def noPresencesBansEmoji =
    new CacheTypeRegistry(noPresencesBansEmojiStickerUpdaters, noBanDeleters)
}

package ackcord.cachehandlers

import scala.language.higherKinds

import scala.reflect.ClassTag

import ackcord.data.raw.PartialUser
import ackcord.data._
import akka.event.LoggingAdapter

class CacheTypeRegistry(
    val updateHandlers: Map[Class[_], CacheUpdater[_]],
    val deleteHandlers: Map[Class[_], CacheDeleter[_]],
    log: LoggingAdapter
) {

  private def handleWithData[D: ClassTag, HandlerTpe[-A] <: CacheHandler[A]](
      handlers: Map[Class[_], HandlerTpe[_]],
      tpe: String,
      data: => D,
      builder: CacheSnapshotBuilder
  ): Unit =
    getWithData[D, HandlerTpe](tpe, handlers).foreach(handler => handler.handle(builder, data, this)(log))

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
      log.debug(s"No $tpe found for ${tag.runtimeClass}")
    }

    res
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

  val allUpdaters: Map[Class[_], CacheUpdater[_]] = Map(
    classOf[PartialUser]      -> CacheHandlers.partialUserUpdater,
    classOf[Guild]            -> CacheHandlers.guildUpdater,
    classOf[Presence]         -> CacheUpdater.dummy[Presence],
    classOf[GuildMember]      -> CacheHandlers.guildMemberUpdater,
    classOf[GuildChannel]     -> CacheHandlers.guildChannelUpdater,
    classOf[DMChannel]        -> CacheHandlers.dmChannelUpdater,
    classOf[GroupDMChannel]   -> CacheHandlers.dmGroupChannelUpdater,
    classOf[User]             -> CacheHandlers.userUpdater,
    classOf[UnavailableGuild] -> CacheHandlers.unavailableGuildUpdater,
    classOf[Ban]              -> CacheUpdater.dummy[Ban],
    classOf[Emoji]            -> CacheUpdater.dummy[Emoji],
    classOf[Message]          -> CacheHandlers.messageUpdater,
    classOf[Role]             -> CacheHandlers.roleUpdater
  )

  val allDeleters: Map[Class[_], CacheDeleter[_]] = Map(
    classOf[GuildChannel]   -> CacheHandlers.guildChannelDeleter,
    classOf[DMChannel]      -> CacheHandlers.dmChannelDeleter,
    classOf[GroupDMChannel] -> CacheHandlers.groupDmChannelDeleter,
    classOf[Ban]            -> CacheDeleter.dummy[Ban],
    classOf[GuildMember]    -> CacheHandlers.guildMemberDeleter,
    classOf[Role]           -> CacheHandlers.roleDeleter,
    classOf[Message]        -> CacheHandlers.messageDeleter
  )

  def default(log: LoggingAdapter) = new CacheTypeRegistry(allUpdaters, allDeleters, log)
}

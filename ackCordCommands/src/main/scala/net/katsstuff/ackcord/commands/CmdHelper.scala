package net.katsstuff.ackcord.commands

import scala.language.higherKinds

import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data.{Message, User}
import net.katsstuff.ackcord.util.MessageParser

object CmdHelper {

  /**
    * Check if a message is a valid command.
    */
  def isValidCommand[F[_]: Monad](needMention: Boolean, msg: Message)(
      implicit c: CacheSnapshotLike[F]
  ): OptionT[F, List[String]] = {
    if (needMention) {
      OptionT.liftF(c.botUser).flatMap { botUser =>
        //We do a quick check first before parsing the message
        val quickCheck =
          OptionT.fromOption(if (msg.mentions.contains(botUser.id)) Some(msg.content.split(" ").toList) else None)

        quickCheck.flatMap { args =>
          MessageParser[User]
            .parse(args)
            .toOption
            .subflatMap {
              case (remaining, user) if user.id == botUser.id => Some(remaining)
              case (_, _)                                     => None
            }
        }
      }
    } else OptionT.some(msg.content.split(" ").toList)
  }
}

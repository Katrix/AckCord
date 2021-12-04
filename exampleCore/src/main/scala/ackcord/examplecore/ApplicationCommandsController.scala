/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord.examplecore

import scala.util.Try

import ackcord.JsonSome
import ackcord.data.raw.RawRole
import ackcord.data.{AllowedMention, InteractionChannel, InteractionGuildMember, UserId}
import ackcord.interactions.ResolvedCommandInteraction
import ackcord.interactions.commands._
import ackcord.requests.Requests
import akka.NotUsed
import cats.Id

class ApplicationCommandsController(requests: Requests) extends CacheApplicationCommandController(requests) {

  val ping: SlashCommand[ResolvedCommandInteraction, NotUsed] =
    SlashCommand.command("ping", "Check if the bot is alive") { _ =>
      sendMessage("Pong")
    }

  val echo: SlashCommand[ResolvedCommandInteraction, String] =
    SlashCommand
      .withParams(string("message", "The message to send back"))
      .command("echo", "Echoes a message you send")(i => sendMessage(s"ECHO: ${i.args}"))

  val nudgeUser: SlashCommand[ResolvedCommandInteraction, InteractionGuildMember] =
    SlashCommand
      .withParams(user("user", "The user to nudge"))
      .command("nudge-user", "Nudge someone") { i =>
        sendMessage(s"Hey ${i.args.user.mention}", allowedMentions = Some(AllowedMention(users = Seq(i.args.user.id))))
      }

  val nudgeRole: SlashCommand[ResolvedCommandInteraction, RawRole] =
    SlashCommand
      .withParams(role("role", "The role to nudge"))
      .command("nudge-role", "Nudge someone") { i =>
        sendMessage(s"Hey ${i.args.id.mention}", allowedMentions = Some(AllowedMention(roles = Seq(i.args.id))))
      }

  val nudge: SlashCommand[ResolvedCommandInteraction, Either[InteractionGuildMember, RawRole]] =
    SlashCommand
      .withParams(mentionable("mentionable", "The one to nudge"))
      .command("nudge", "Nudge someone") { i =>
        sendMessage(
          s"Hey ${i.args.fold(_.user.mention, _.id.mention)}",
          allowedMentions =
            Some(AllowedMention(users = i.args.swap.map(_.user.id).toSeq, roles = i.args.map(_.id).toSeq))
        )
      }

  val mentionChannel: SlashCommand[ResolvedCommandInteraction, Id[InteractionChannel]] = SlashCommand
    .withParams(channel("channel", "The channel to mention"))
    .command("mention-channel", "Mention a channel") { i =>
      sendMessage(s"Channel: ${i.args.id.mention}")
    }

  val asyncTest: SlashCommand[ResolvedCommandInteraction, NotUsed] =
    SlashCommand.command("async", "An async test command") { implicit i =>
      async(implicit token => sendAsyncMessage("Async message"))
    }

  val asyncEditTest: SlashCommand[ResolvedCommandInteraction, (String, String)] =
    SlashCommand
      .withParams(string("par1", "The first parameter") ~ string("par2", "The second parameter"))
      .command("asyncEdit", "An async edit test command") { implicit i =>
        sendMessage("An instant message").doAsync { implicit token =>
          editOriginalMessage(content = JsonSome("An instant message (with an edit)"))
        }
      }

  val groupTest: SlashCommandGroup = SlashCommand.group("group", "Group test")(
    SlashCommand.command("foo", "Sends foo")(_ => sendMessage("Foo")),
    SlashCommand.command("bar", "Sends bar")(_ => sendMessage("Bar"))
  )

  val nudgeUserCommand: UserCommand[ResolvedCommandInteraction] = UserCommand.handle("nudge") { i =>
    sendMessage(s"Hey ${i.args._1.mention}", allowedMentions = Some(AllowedMention(users = Seq(i.args._1.id))))
  }

  val echoMessage: MessageCommand[ResolvedCommandInteraction] = MessageCommand.handle("echo") { i =>
    sendMessage(s"ECHO: ${i.args.content}")
  }

  val simpleAutocomplete: SlashCommand[ResolvedCommandInteraction, String] = SlashCommand
    .withParams(string("auto", "An autocomplete parameter").withAutocomplete(s => Seq(s * 2, s * 3, s * 4)))
    .command("simple-autocomplete", "A simple autocomplete command") { i =>
      sendMessage(s"Res: ${i.args}")
    }

  val multiParamsAutocomplete: SlashCommand[ResolvedCommandInteraction, (String, String)] = SlashCommand
    .withParams(
      string("auto1", "An autocomplete parameter").withAutocomplete(s => Seq(s * 2, s * 3, s * 4)) ~
        string("auto2", "Another autocomplete parameter").withAutocomplete(s =>
          Seq(s.substring(0, math.max(0, s.length - 1)), s.substring(0, math.max(0, s.length - 2)), s.substring(0, math.max(0, s.length - 3)))
        )
    )
    .command("multi-autocomplete", "A simple autocomplete command") { i =>
      sendMessage(s"Res: ${i.args._1} ${i.args._2}")
    }

  val intAutocomplete: SlashCommand[ResolvedCommandInteraction, Int] = SlashCommand
    .withParams(int("auto", "An autocomplete parameter").withAutocomplete(i => Try(i.toInt).toEither.toSeq.flatMap(i => Seq(i, i * 10, i * 1000))))
    .command("int-autocomplete", "An int autocomplete command") { i =>
      sendMessage(s"Res: ${i.args}")
    }
}

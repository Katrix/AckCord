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

import ackcord.JsonSome
import ackcord.data.{AllowedMention, UserId}
import ackcord.requests.Requests
import ackcord.slashcommands.ResolvedCommandInteraction
import ackcord.slashcommands.commands.{CacheSlashCommandController, Command, CommandGroup}
import akka.NotUsed

class SlashCommandsController(requests: Requests) extends CacheSlashCommandController(requests) {

  val ping: Command[ResolvedCommandInteraction, NotUsed] = Command.command("ping", "Check if the bot is alive") { _ =>
    sendMessage("Pong")
  }

  val echo: Command[ResolvedCommandInteraction, String] =
    Command
      .withParams(string("message", "The message to send back"))
      .command("echo", "Echoes a message you send")(i => sendMessage(s"ECHO: ${i.args}"))

  val nudge: Command[ResolvedCommandInteraction, UserId] =
    Command
      .withParams(user("user", "The user to nudge"))
      .command("nudge", "Nudge someone") { i =>
        sendMessage(s"Hey ${i.args.mention}", allowedMentions = Some(AllowedMention(users = Seq(i.args))))
      }

  val asyncTest: Command[ResolvedCommandInteraction, NotUsed] =
    Command.command("async", "An async test command") { implicit i =>
      async(implicit token => sendAsyncMessage("Async message"))
    }

  val asyncEditTest: Command[ResolvedCommandInteraction, (String, String)] =
    Command
      .withParams(string("par1", "The first parameter") ~ string("par2", "The second parameter"))
      .command("asyncEdit", "An async edit test command") { implicit i =>
        sendMessage("An instant message").doAsync { implicit token =>
          editOriginalMessage(content = JsonSome("An instant message (with an edit)"))
        }
      }

  val groupTest: CommandGroup = Command.group("group", "Group test")(
    Command.command("foo", "Sends foo")(_ => sendMessage("Foo")),
    Command.command("bar", "Sends bar")(_ => sendMessage("Bar"))
  )
}

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
package ackcord.example

import scala.concurrent.Future

import ackcord.commands.HelpCommand
import ackcord.data.{EmbedField, Message, OutgoingEmbed, OutgoingEmbedFooter}
import ackcord.requests.CreateMessageData
import ackcord.{CacheSnapshot, Requests}

class MyHelpCommand(requests: Requests) extends HelpCommand(requests) {
  override def createSearchReply(
      message: Message,
      query: String,
      matches: Seq[HelpCommand.HelpCommandProcessedEntry]
  )(implicit
      c: CacheSnapshot
  ): Future[CreateMessageData] = Future.successful(
    CreateMessageData(
      embeds = Seq(
        OutgoingEmbed(
          title = Some(s"Commands matching: $query"),
          fields = matches.map(createContent(_))
        )
      )
    )
  )

  override def createReplyAll(message: Message, page: Int)(implicit
      c: CacheSnapshot
  ): Future[CreateMessageData] = {
    if (page <= 0) {
      Future.successful(
        CreateMessageData(embeds =
          Seq(OutgoingEmbed(description = Some("Invalid Page")))
        )
      )
    } else {
      Future
        .traverse(registeredCommands.toSeq) { entry =>
          entry.prefixParser
            .canExecute(c, message)
            .zip(entry.prefixParser.needsMention(c, message))
            .zip(entry.prefixParser.symbols(c, message))
            .zip(entry.prefixParser.aliases(c, message))
            .zip(entry.prefixParser.caseSensitive(c, message))
            .map(entry -> _)
        }
        .map { entries =>
          val commandSlice = entries
            .collect {
              case (
                    entry,
                    (
                      (((canExecute, needsMention), symbols), aliases),
                      caseSensitive
                    )
                  ) if canExecute =>
                HelpCommand.HelpCommandProcessedEntry(
                  needsMention,
                  symbols,
                  aliases,
                  caseSensitive,
                  entry.description
                )
            }
            .sortBy(e => (e.symbols.head, e.aliases.head))
            .slice((page - 1) * 10, page * 10)

          val maxPages =
            Math.max(Math.ceil(registeredCommands.size / 10D).toInt, 1)
          if (commandSlice.isEmpty) {
            CreateMessageData(s"Max pages: $maxPages")
          } else {

            CreateMessageData(
              embeds = Seq(
                OutgoingEmbed(
                  fields = commandSlice.map(createContent(_)),
                  footer =
                    Some(OutgoingEmbedFooter(s"Page: $page of $maxPages"))
                )
              )
            )
          }
        }
    }
  }

  def createContent(
      entry: HelpCommand.HelpCommandProcessedEntry
  )(implicit c: CacheSnapshot): EmbedField = {
    val invocation = {
      val mention = if (entry.needsMention) s"${c.botUser.mention} " else ""
      val symbol =
        if (entry.symbols.length > 1) entry.symbols.mkString("(", "|", ")")
        else entry.symbols.head
      val alias =
        if (entry.aliases.length > 1) entry.aliases.mkString("(", "|", ")")
        else entry.aliases.head

      mention + symbol + alias
    }

    val builder = new StringBuilder
    builder.append(s"Name: ${entry.description.name}\n")
    builder.append(s"Description: ${entry.description.description}\n")
    builder.append(s"Usage: $invocation ${entry.description.usage}\n")

    EmbedField(entry.description.name, builder.mkString)
  }
}

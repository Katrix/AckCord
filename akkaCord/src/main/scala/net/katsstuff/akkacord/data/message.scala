/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.akkacord.data

import java.time.OffsetDateTime

sealed trait Author
case class WebhookAuthor(id:   Snowflake, name: String, avatar: String) extends Author
case class User(id:            Snowflake,
                username:      String,
                discriminator: String,
                avatar:        String,
                bot:           Option[Boolean], //Bot can be missing
                mfaEnabled:    Option[Boolean], //mfaEnabled can be missing
                verified:      Option[Boolean], //verified can be missing
                email:         Option[String]) //Email can be null
    extends Author

case class Message(id:              Snowflake,
                   channelId:       Snowflake,
                   author:          Author, //TODO: Factor webhook messages and normal messages into separate classes, to remove a reference to a user
                   content:         String,
                   timestamp:       OffsetDateTime,
                   editedTimestamp: Option[OffsetDateTime],
                   tts:             Boolean,
                   mentionEveryone: Boolean,
                   mentions:        Seq[Snowflake],
                   mentionRoles:    Seq[Snowflake],
                   attachment:      Seq[Attachment],
                   embeds:          Seq[Embed],
                   reactions:       Seq[Reaction],
                   nonce:           Option[Snowflake],
                   pinned:          Boolean,
                   webhookId:       Option[String]) extends GetChannel

case class Reaction(count:  Int, me:                 Boolean, emoji: MessageEmoji)
case class MessageEmoji(id: Option[Snowflake], name: String)

case class Embed(title:       String,
                 embedType:   String,
                 description: String,
                 url:         String,
                 timestamp:   OffsetDateTime,
                 color:       Int,
                 footer:      EmbedFooter,
                 image:       EmbedImage,
                 thumbnail:   EmbedThumbnail,
                 video:       EmbedVideo,
                 provider:    EmbedProvider,
                 author:      EmbedAuthor,
                 fields:      Seq[EmbedField])

case class EmbedThumbnail(url: String, proxyUrl: String, height: Int, width: Int)
case class EmbedVideo(url:     String, height:   Int, width: Int)
case class EmbedImage(url:     String, proxyUrl: String, height: Int, width: Int)
case class EmbedProvider(name: String, url:      String)
case class EmbedAuthor(name:   String, url:      String, iconUrl: String, proxyIconUrl: String)
case class EmbedFooter(text:   String, iconUrl:  String, proxyIconUrl: String)
case class EmbedField(name:    String, value:    String, inline: Boolean)

case class Attachment(id: Snowflake, filename: String, size: Int, url: String, proxyUrl: String, height: Option[Int], width: Option[Int])

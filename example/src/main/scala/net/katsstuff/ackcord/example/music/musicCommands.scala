/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.example.music

import scala.concurrent.Future

import akka.actor.ActorRef
import akka.stream.scaladsl.{Flow, Sink}
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.commands.{CmdDescription, CmdFilter, ParsedCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.data.{CacheSnapshot, GuildId, UserId, VoiceState}
import net.katsstuff.ackcord.example.ExampleCmdCategories
import net.katsstuff.ackcord.example.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.http.requests.RequestStreams
import net.katsstuff.ackcord.syntax._



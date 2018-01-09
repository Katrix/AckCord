/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.benchmark

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.entities.impl.JDAImpl
import net.katsstuff.ackcord.SnowflakeMap
import okhttp3.OkHttpClient
import sx.blah.discord.api.internal.json.requests.PresenceUpdateRequest
import sx.blah.discord.api.internal.{DiscordClientImpl, ShardImpl}
import sx.blah.discord.api.{ClientBuilder, IDiscordClient}

object MemoryTest extends App {
  println("Started")

  val userNum = 2000

  val users = SnowflakeMap(
    Seq
      .tabulate(userNum)(identity)
      .par
      .map(_ => GenData.randomUser())
      .seq
      .map(u => u.id -> u): _*
  )
  val guilds = SnowflakeMap(Seq.fill(100)(GenData.randomGuild(users.values.toSeq, 128)).map(g => g.id -> g): _*)

  val client = new ClientBuilder().withToken("").build().asInstanceOf[DiscordClientImpl]
  val ctor = classOf[ShardImpl]
    .getDeclaredConstructor(
      classOf[IDiscordClient],
      classOf[String],
      classOf[Array[Int]],
      classOf[PresenceUpdateRequest]
    )
  ctor.setAccessible(true)
  val shard = ctor.newInstance(client, null, null, null)

  guilds.values.foreach(g => D4JConverter.convert(g, users.filter(t => guilds.exists(_._2.members.contains(t._1))), shard))

  val jdaImpl = new JDAImpl(AccountType.BOT, new OkHttpClient.Builder, null, false, false, false, false, 2, 900)

  guilds.values.foreach(g => JDAConverter.convert(g, users.filter(t => guilds.exists(_._2.members.contains(t._1))), jdaImpl))
  println("Done")
  Console.in.readLine()

  println(jdaImpl)
}

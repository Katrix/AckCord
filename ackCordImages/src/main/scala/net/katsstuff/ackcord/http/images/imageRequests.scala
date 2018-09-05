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
package net.katsstuff.ackcord.http.images

import scala.language.higherKinds

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, RequestEntity, ResponseEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import cats.Monad
import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.data.{EmojiId, GuildId, ImageFormat, RawSnowflake, UserId}
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.{Request, RequestRoute}
import net.katsstuff.ackcord.util.MapWithMaterializer

/**
  * Base traits for all traits to get images
  */
trait ImageRequest[Ctx] extends Request[ByteString, Ctx] {
  require(desiredSize >= 16 && desiredSize <= 2048, "Can't request an image smaller than 16 or bigger than 2048")
  require(ImageRequest.isPowerOf2(desiredSize), "Can only request an image sizes that are powers of 2")
  require(allowedFormats.contains(format), "That format is not allowed for this image")

  /**
    * The desired size of the image. Must be between 16 and 2048, and must be a power of 2.
    */
  def desiredSize: Int

  /**
    * The format to get the image in.
    */
  def format: ImageFormat

  /**
    * The allowed formats for this image.
    */
  def allowedFormats: Seq[ImageFormat]

  override def requestBody: RequestEntity = HttpEntity.Empty

  override def bodyForLogging: Option[String] = None

  override def parseResponse(
      parallelism: Int
  )(implicit system: ActorSystem): Flow[ResponseEntity, ByteString, NotUsed] = {
    MapWithMaterializer
      .flow { implicit mat => response: ResponseEntity =>
        import mat.executionContext
        Unmarshal(response).to[ByteString]
      }
      .mapAsyncUnordered(parallelism)(identity)
  }
  
  override def hasPermissions[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): F[Boolean] = F.pure(true)
}
object ImageRequest {
  //https://stackoverflow.com/questions/600293/how-to-check-if-a-number-is-a-power-of-2
  private def isPowerOf2(num: Int): Boolean = (num & (num - 1)) == 0
}

/**
  * Get the image of a custom emoji. Always returns a PNG.
  */
case class GetCustomEmojiImage[Ctx](
    desiredSize: Int,
    format: ImageFormat,
    emojiId: EmojiId,
    context: Ctx = NotUsed: NotUsed
) extends ImageRequest[Ctx] {
  override def route: RequestRoute              = Routes.emojiImage(emojiId, format, desiredSize)
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.GIF)
}

/**
  * Get a guild icon image. Allowed formats are PNG, JPEG and WebP.
  */
case class GetGuildIconImage[Ctx](
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    iconHash: String,
    context: Ctx = NotUsed: NotUsed
) extends ImageRequest[Ctx] {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.guildIconImage(guildId, iconHash, format, desiredSize)
}

/**
  * Get a guild splash image. Allowed formats are PNG, JPEG and WebP.
  */
case class GetGuildSplashImage[Ctx](
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    splashHash: String,
    context: Ctx = NotUsed: NotUsed
) extends ImageRequest[Ctx] {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.guildSplashImage(guildId, splashHash, format, desiredSize)
}

/**
  * Get the default avatar of a user. Always returns a PNG.
  */
case class GetDefaultUserAvatarImage[Ctx](desiredSize: Int, discriminator: Int, context: Ctx = NotUsed: NotUsed)
    extends ImageRequest[Ctx] {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG)
  override def format: ImageFormat              = ImageFormat.PNG
  override def route: RequestRoute              = Routes.defaultUserAvatarImage(discriminator, format, desiredSize)
}

/**
  * Get the image of a user avatar. Allowed formats are PNG, JPEG, WebP and GIF.
  */
case class GetUserAvatarImage[Ctx](
    desiredSize: Int,
    format: ImageFormat,
    userId: UserId,
    avatarHash: String,
    context: Ctx = NotUsed: NotUsed
) extends ImageRequest[Ctx] {
  override def allowedFormats: Seq[ImageFormat] =
    Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP, ImageFormat.GIF)
  override def route: RequestRoute = Routes.userAvatarImage(userId, avatarHash, format, desiredSize)
}

/**
  * Get the icon of an application. Allowed formats are PNG, JPEG and WebP.
  */
case class GetApplicationIconImage[Ctx](
    desiredSize: Int,
    format: ImageFormat,
    applicationId: RawSnowflake,
    iconHash: String,
    context: Ctx = NotUsed: NotUsed
) extends ImageRequest[Ctx] {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.applicationIconImage(applicationId, iconHash, format, desiredSize)
}

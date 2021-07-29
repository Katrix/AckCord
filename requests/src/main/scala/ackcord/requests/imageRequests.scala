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
package ackcord.requests

import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.data._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, RequestEntity, ResponseEntity}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

/** Base traits for all traits to get images */
trait ImageRequest extends Request[ByteString] {
  require(desiredSize >= 16 && desiredSize <= 4096, "Can't request an image smaller than 16 or bigger than 4096")
  require(ImageRequest.isPowerOf2(desiredSize), "Can only request an image sizes that are powers of 2")
  require(allowedFormats.contains(format), "That format is not allowed for this image")

  /** The desired size of the image. Must be between 16 and 2048, and must be a power of 2. */
  def desiredSize: Int

  /** The format to get the image in. */
  def format: ImageFormat

  /** The allowed formats for this image. */
  def allowedFormats: Seq[ImageFormat]

  override def requestBody: RequestEntity = HttpEntity.Empty

  override def bodyForLogging: Option[String] = None

  override def parseResponse(entity: ResponseEntity)(implicit system: ActorSystem[Nothing]): Future[ByteString] =
    Source.single(entity).via(RequestStreams.bytestringFromResponse).runWith(Sink.head)

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = true
}
object ImageRequest {
  //https://stackoverflow.com/questions/600293/how-to-check-if-a-number-is-a-power-of-2
  private def isPowerOf2(num: Int): Boolean = (num & (num - 1)) == 0
}

/** Get the image of a custom emoji. Always returns a PNG. */
case class GetCustomEmojiImage(
    desiredSize: Int,
    format: ImageFormat,
    emojiId: EmojiId
) extends ImageRequest {
  override def route: RequestRoute = Routes.emojiImage(emojiId, format, Some(desiredSize))
  override def allowedFormats: Seq[ImageFormat] =
    Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP, ImageFormat.GIF)
}

/** Get a guild icon image. Allowed formats are PNG, JPEG and WebP. */
case class GetGuildIconImage(
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    iconHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] =
    Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP, ImageFormat.GIF)
  override def route: RequestRoute = Routes.guildIconImage(guildId, iconHash, format, Some(desiredSize))
}

/** Get a guild splash image. Allowed formats are PNG, JPEG and WebP. */
case class GetGuildSplashImage(
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    splashHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.guildSplashImage(guildId, splashHash, format, Some(desiredSize))
}

case class GetDiscoverySplashImage(
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    splashHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.discoverySplashImage(guildId, splashHash, format, Some(desiredSize))
}

case class GetGuildBannerImage(
    desiredSize: Int,
    format: ImageFormat,
    guildId: GuildId,
    bannerHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.guildBannerImage(guildId, bannerHash, format, Some(desiredSize))
}

/** Get the default avatar of a user. Always returns a PNG. */
case class GetDefaultUserAvatarImage(desiredSize: Int, discriminator: Int) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG)
  override def format: ImageFormat              = ImageFormat.PNG
  override def route: RequestRoute              = Routes.defaultUserAvatarImage(discriminator, format, Some(desiredSize))
}

/** Get the image of a user avatar. Allowed formats are PNG, JPEG, WebP and GIF. */
case class GetUserAvatarImage(
    desiredSize: Int,
    format: ImageFormat,
    userId: UserId,
    avatarHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] =
    Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP, ImageFormat.GIF)
  override def route: RequestRoute = Routes.userAvatarImage(userId, avatarHash, format, Some(desiredSize))
}

/** Get the icon of an application. Allowed formats are PNG, JPEG and WebP. */
case class GetApplicationIconImage(
    desiredSize: Int,
    format: ImageFormat,
    applicationId: ApplicationId,
    iconHash: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.applicationIconImage(applicationId, iconHash, format, Some(desiredSize))
}

/** Get the asset of an application. Allowed formats are PNG, JPEG and WebP. */
case class GetApplicationAssetImage(
    desiredSize: Int,
    format: ImageFormat,
    applicationId: ApplicationId,
    assetId: String
) extends ImageRequest {
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
  override def route: RequestRoute              = Routes.applicationAssetImage(applicationId, assetId, format, Some(desiredSize))
}

/**
  * Get the widget image for a specific guild.
  * @param guildId The guild to get the widget for.
  * @param style Which style should be gotten.
  */
case class GetGuildWidgetImage(
    guildId: GuildId,
    style: WidgetImageStyle = WidgetImageStyle.Shield
) extends ImageRequest {
  override def route: RequestRoute = Routes.getGuildWidgetImage(guildId, Some(style))

  //Dummy fields which aren't used
  override def desiredSize: Int                 = 16
  override def format: ImageFormat              = ImageFormat.PNG
  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG)
}

/**
  * Get the icon for a team
  * @param teamId The id of the team to get the icon for
  * @param teamIcon The icon identifier
  */
case class GetTeamIconImage(
    desiredSize: Int,
    format: ImageFormat,
    teamId: SnowflakeType[Team],
    teamIcon: String
) extends ImageRequest {
  override def route: RequestRoute = Routes.teamIconImage(teamId, teamIcon, format, Some(desiredSize))

  override def allowedFormats: Seq[ImageFormat] = Seq(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WebP)
}

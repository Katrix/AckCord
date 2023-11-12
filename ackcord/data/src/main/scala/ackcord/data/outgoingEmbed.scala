package ackcord.data

import java.time.OffsetDateTime

import ackcord.data.Embed.EmbedField
import io.circe._
import io.circe.syntax._

/**
  * An outgoing embed.
  *
  * @param title
  *   The title of the embed.
  * @param description
  *   The embed description or main text.
  * @param url
  *   The url of the embed.
  * @param timestamp
  *   The timestamp of the embed.
  * @param color
  *   The color of the embed
  * @param footer
  *   The footer part of the embed.
  * @param image
  *   The image part of the embed.
  * @param thumbnail
  *   The thumbnail part of the embed.
  * @param author
  *   The author part of the embed.
  * @param fields
  *   The fields of the embed.
  */
case class OutgoingEmbed(
    title: Option[String] = None,
    description: Option[String] = None,
    url: Option[String] = None,
    timestamp: Option[OffsetDateTime] = None,
    color: Option[Int] = None,
    footer: Option[OutgoingEmbedFooter] = None,
    image: Option[OutgoingEmbedImage] = None,
    video: Option[OutgoingEmbedVideo] = None,
    thumbnail: Option[OutgoingEmbedThumbnail] = None,
    author: Option[OutgoingEmbedAuthor] = None,
    fields: Seq[EmbedField] = Seq.empty
) {
  Verifier.requireLengthO(title, "Embed title", max = 256)
  Verifier.requireLengthO(title, "Embed description", max = 4096)
  Verifier.requireLengthS(fields, "Embed fields", max = 25)
  require(totalCharAmount <= 6000, "An embed can't have more than 6000 characters in total")

  /** The total amount of characters in this embed so far. */
  def totalCharAmount: Int = {
    val fromTitle       = title.fold(0)(Verifier.stringLength)
    val fromDescription = description.fold(0)(Verifier.stringLength)
    val fromFooter      = footer.fold(0)(f => Verifier.stringLength(f.text))
    val fromAuthor      = author.fold(0)(a => Verifier.stringLength(a.name))
    val fromFields      = fields.map(f => Verifier.stringLength(f.name) + Verifier.stringLength(f.value)).sum

    fromTitle + fromDescription + fromFooter + fromAuthor + fromFields
  }
}
object OutgoingEmbed {
  implicit val codec: Codec[OutgoingEmbed] = Codec.from(
    (c: HCursor) =>
      for {
        title       <- c.get[UndefOr[String]]("title")
        description <- c.get[UndefOr[String]]("description")
        url         <- c.get[UndefOr[String]]("url")
        timestamp   <- c.get[UndefOr[OffsetDateTime]]("timestamp")
        color       <- c.get[UndefOr[Int]]("color")
        footer      <- c.get[UndefOr[OutgoingEmbedFooter]]("footer")
        image       <- c.get[UndefOr[OutgoingEmbedImage]]("image")
        video       <- c.get[UndefOr[OutgoingEmbedVideo]]("video")
        thumbnail   <- c.get[UndefOr[OutgoingEmbedThumbnail]]("thumbnail")
        author      <- c.get[UndefOr[OutgoingEmbedAuthor]]("author")
        fields      <- c.get[UndefOr[Seq[EmbedField]]]("fields")
      } yield OutgoingEmbed(
        title.toOption,
        description.toOption,
        url.toOption,
        timestamp.toOption,
        color.toOption,
        footer.toOption,
        image.toOption,
        video.toOption,
        thumbnail.toOption,
        author.toOption,
        fields.getOrElse(Nil)
      ),
    (a: OutgoingEmbed) => {
      Json.obj(
        Seq(
          a.title.map("title"             := _),
          a.description.map("description" := _),
          a.url.map("url"                 := _),
          a.timestamp.map("timestamp"     := _),
          a.color.map("color"             := _),
          a.footer.map("footer"           := _),
          a.image.map("image"             := _),
          a.video.map("video"             := _),
          a.thumbnail.map("thumbnail"     := _),
          a.author.map("author"           := _),
          if (a.fields.nonEmpty) Some("fields" := a.fields) else None
        ).flatten: _*
      )
    }
  )
}

/**
  * The thumbnail part of an outgoing embed.
  * @param url
  *   The url to the thumbnail.
  */
case class OutgoingEmbedThumbnail(url: String)
object OutgoingEmbedThumbnail {
  implicit val codec: Codec[OutgoingEmbedThumbnail] =
    Codec.from(
      (c: HCursor) => c.get[String]("url").map(OutgoingEmbedThumbnail(_)),
      (a: OutgoingEmbedThumbnail) => Json.obj("url" := a.url)
    )
}

/**
  * The image part of an outgoing embed.
  * @param url
  *   The url to the image.
  */
case class OutgoingEmbedImage(url: String)
object OutgoingEmbedImage {
  implicit val codec: Codec[OutgoingEmbedImage] =
    Codec.from(
      (c: HCursor) => c.get[String]("url").map(OutgoingEmbedImage(_)),
      (a: OutgoingEmbedImage) => Json.obj("url" := a.url)
    )
}

/**
  * The video part of an outgoing embed.
  * @param url
  *   The url to the video.
  */
case class OutgoingEmbedVideo(url: String)
object OutgoingEmbedVideo {
  implicit val codec: Codec[OutgoingEmbedVideo] =
    Codec.from(
      (c: HCursor) => c.get[String]("url").map(OutgoingEmbedVideo(_)),
      (a: OutgoingEmbedVideo) => Json.obj("url" := a.url)
    )
}

/**
  * The author part of an outgoing embed
  * @param name
  *   The name of the author
  * @param url
  *   The url to link when clicking on the author
  * @param iconUrl
  *   The icon to show besides the author.
  */
case class OutgoingEmbedAuthor(name: String, url: Option[String] = None, iconUrl: Option[String] = None) {
  Verifier.requireLength(name, "Embed author name", max = 256)
}
object OutgoingEmbedAuthor {
  implicit val codec: Codec[OutgoingEmbedAuthor] = Codec.from(
    (c: HCursor) =>
      for {
        text    <- c.get[String]("text")
        url     <- c.get[UndefOr[String]]("url")
        iconUrl <- c.get[UndefOr[String]]("icon_url")
      } yield OutgoingEmbedAuthor(text, url.toOption, iconUrl.toOption),
    (a: OutgoingEmbedAuthor) =>
      Json.obj(List("name" := a.name) ++ a.url.map("url" := _) ++ a.iconUrl.toList.map("icon_url" := _): _*)
  )
}

/**
  * The footer part of an outgoing embed.
  * @param text
  *   The text of the footer
  * @param iconUrl
  *   The icon url of the footer.
  */
case class OutgoingEmbedFooter(text: String, iconUrl: Option[String] = None) {
  Verifier.requireLength(text, "Embed footer text", max = 2048)
}
object OutgoingEmbedFooter {
  implicit val codec: Codec[OutgoingEmbedFooter] = Codec.from(
    (c: HCursor) =>
      for {
        text    <- c.get[String]("text")
        iconUrl <- c.get[UndefOr[String]]("icon_url")
      } yield OutgoingEmbedFooter(text, iconUrl.toOption),
    (a: OutgoingEmbedFooter) => Json.obj(List("text" := a.text) ++ a.iconUrl.toList.map("icon_url" := _): _*)
  )
}

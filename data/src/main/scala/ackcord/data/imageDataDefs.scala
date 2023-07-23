package ackcord.data

import java.util.Base64

import ackcord.data.base.DiscordOpaqueCompanion

trait ImageDataDefs {
  type ImageData = ImageDataDefs.OpaqueType
  val ImageData: ImageDataDefs.type = ImageDataDefs
}
object ImageDataDefs extends DiscordOpaqueCompanion[String] {
  def from(imageType: ImageFormat, data: Array[Byte]): ImageData = {
    val base64Data = Base64.getEncoder.encodeToString(data)
    apply(s"data:${imageType.base64Name};base64,$base64Data")
  }
}

sealed trait ImageFormat {
  def extensions: Seq[String]
  def extension: String = extensions.head
  def base64Name: String
}
object ImageFormat {
  case object JPEG extends ImageFormat {
    override def extensions: Seq[String] = Seq("jpg", "jpeg")
    override def base64Name: String      = "image/jpeg"
  }
  case object PNG extends ImageFormat {
    override def extensions: Seq[String] = Seq("png")
    override def base64Name: String      = "image/png"
  }
  case object WebP extends ImageFormat {
    override def extensions: Seq[String] = Seq("webp")
    override def base64Name: String = throw new IllegalArgumentException("WepP is not supported as Base64 image data")
  }
  case object GIF extends ImageFormat {
    override def extensions: Seq[String] = Seq("gif")
    override def base64Name: String      = "image/gif"
  }
  case object Lottie extends ImageFormat {
    override def extensions: Seq[String] = Seq("json")
    override def base64Name: String = throw new IllegalArgumentException("Lottie is not supported as Base64 image data")
  }
}

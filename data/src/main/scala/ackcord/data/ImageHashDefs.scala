package ackcord.data

import ackcord.data.base.DiscordOpaqueCompanion

trait ImageHashDefs {
  type ImageHash = ImageHashDefs.OpaqueType
}
object ImageHashDefs extends DiscordOpaqueCompanion[String]

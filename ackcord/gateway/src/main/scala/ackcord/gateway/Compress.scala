package ackcord.gateway

sealed trait Compress
object Compress {
  case object NoCompress         extends Compress
  case object PerMessageCompress extends Compress
  case object ZLibStreamCompress extends Compress
}

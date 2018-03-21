package net.katsstuff.ackcord

/**
  * A handler for a specific event type.
  * @tparam A The API message type
  * @tparam B The return type, which may or may not be used for other stuff
  */
trait EventHandler[A <: APIMessage, B] {

  /**
    * Called whenever the event for this handler is received.
    * @param message The event itself.
    * @param c A cache snapshot associated with the event.
    */
  def handle(message: A)(implicit c: CacheSnapshot): B
}

/**
  * A handler for a specific event type that runs a [[RequestDSL]] when the event is received.
  * @tparam A The API message type
  * @tparam B The return type, which may or may not be used for other stuff
  */
trait EventHandlerDSL[A <: APIMessage, B] {

  /**
    * Runs the [[RequestDSL]] whenever the event for this handler is received.
    * @param message The event itself.
    * @param c A cache snapshot associated with the event.
    */
  def handle(message: A)(implicit c: CacheSnapshot): RequestDSL[B]
}
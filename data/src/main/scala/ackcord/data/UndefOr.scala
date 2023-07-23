package ackcord.data

import io.circe.{Decoder, HCursor}

trait UndefOr[+A] {
  def isUndefined: Boolean
  def isEmpty: Boolean  = isUndefined
  def nonEmpty: Boolean = !isEmpty

  def toOption: Option[A]

  def fold[B](ifUndefined: => B)(f: A => B): B

  def map[B](f: A => B): UndefOr[B]
  def flatMap[B](f: A => UndefOr[B]): UndefOr[B]

  def filterToUndefined(f: A => Boolean): UndefOr[A]

  def contains[A1 >: A](value: A1): Boolean
  def exists[A1 >: A](f: A1 => Boolean): Boolean
  def forall[A1 >: A](f: A1 => Boolean): Boolean

  def foreach[A1 >: A](f: A1 => Unit): Unit

  def getOrElse[B >: A](other: => B): B
  def orElse[B >: A](other: => UndefOr[B]): UndefOr[B]

  def toList[A1 >: A]: List[A]
}
object UndefOr {
  implicit def undefOrDecoder[A: Decoder]: Decoder[UndefOr[A]] = (c: HCursor) =>
    if (c.succeeded) c.as[A].map(UndefOrSome(_)) else Right(UndefOrUndefined)
}

case class UndefOrSome[A](value: A) extends UndefOr[A] {
  override def isUndefined: Boolean = false

  override def toOption: Option[A] = Some(value)

  override def fold[B](ifNull: => B)(f: A => B): B = f(value)

  override def map[B](f: A => B): UndefOr[B]              = UndefOrSome(f(value))
  override def flatMap[B](f: A => UndefOr[B]): UndefOr[B] = f(value)

  override def filterToUndefined(f: A => Boolean): UndefOr[A] = if (f(value)) UndefOrSome(value) else UndefOrUndefined

  override def contains[A1 >: A](value: A1): Boolean      = this.value == value
  override def exists[A1 >: A](f: A1 => Boolean): Boolean = f(value)
  override def forall[A1 >: A](f: A1 => Boolean): Boolean = f(value)

  override def foreach[A1 >: A](f: A1 => Unit): Unit = f(value)

  override def getOrElse[B >: A](other: => B): B                = value
  override def orElse[B >: A](other: => UndefOr[B]): UndefOr[B] = this

  override def toList[A1 >: A]: List[A] = List(value)
}

case object UndefOrUndefined extends UndefOr[Nothing] {
  override def isUndefined: Boolean = true

  override def toOption: Option[Nothing] = None

  override def fold[B](ifUndefined: => B)(f: Nothing => B): B = ifUndefined

  override def map[B](f: Nothing => B): UndefOr[B]              = this
  override def flatMap[B](f: Nothing => UndefOr[B]): UndefOr[B] = this

  override def filterToUndefined(f: Nothing => Boolean): UndefOr[Nothing] = this

  override def contains[A1 >: Nothing](value: A1): Boolean      = false
  override def exists[A1 >: Nothing](f: A1 => Boolean): Boolean = false
  override def forall[A1 >: Nothing](f: A1 => Boolean): Boolean = true

  override def foreach[A1 >: Nothing](f: A1 => Unit): Unit = ()

  override def getOrElse[B >: Nothing](other: => B): B                = other
  override def orElse[B >: Nothing](other: => UndefOr[B]): UndefOr[B] = other

  override def toList[A1 >: Nothing]: List[Nothing] = Nil
}

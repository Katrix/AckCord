trait Show[A] {
  def show(a: A): String
}
object Show {
  def apply[A](implicit show: Show[A]): Show[A] = show
  def mkShow[A](f: A => String): Show[A] = new Show[A] {
    override def show(a: A): String = f(a)
  }

  implicit val intShow: Show[Int] = mkShow(_.toString)
  implicit val stringShow: Show[String] = mkShow(identity)
}

case class CombinedClass(int: Int, str: String)

case class InnerCombinedClass(int1: Int, int2: Int)
case class OuterCombinedClass(str: String, inner: InnerCombinedClass)

sealed trait HList
final case class ::[+H, +T <: HList](head: H, tail: T) extends HList
sealed trait HNil extends HList {
  def ::[H1](h: H1): H1 :: HNil = new ::(h, this)
}
case object HNil extends HNil

class HListOps[L <: HList](val l: L) {
  def ::[H](h: H): H :: L = new ::(h, l)
}

implicit def toOps[L <: HList](hList: L): HListOps[L] = new HListOps[L](hList)

sealed trait Generic[A] {
  type Repr
  def to(a: A): Repr
  def from(repr: Repr): A
}
object Generic {
  type Aux[A, Repr0] = Generic[A] { type Repr = Repr0}
  def apply[A](implicit gen: Generic[A]): Aux[A, gen.Repr] = gen
}

implicit val genCombinedClass = new Generic[CombinedClass] {
  override type Repr = Int :: String :: HNil
  override def to(a: CombinedClass): Int :: String :: HNil = a.int :: a.str :: HNil
  override def from(repr: Int :: String :: HNil): CombinedClass = CombinedClass(repr.head, repr.tail.head)
}

implicit val genOuterCombinedClass = new Generic[OuterCombinedClass] {
  override type Repr = String :: InnerCombinedClass :: HNil
  override def to(a: OuterCombinedClass): String :: InnerCombinedClass :: HNil = a.str :: a.inner :: HNil
  override def from(repr: String :: InnerCombinedClass :: HNil): OuterCombinedClass = OuterCombinedClass(repr.head, repr.tail.head)
}

implicit val genInnerCombinedClass = new Generic[InnerCombinedClass] {
  override type Repr = Int :: Int :: HNil
  override def to(a: InnerCombinedClass): Int :: Int :: HNil = a.int1 :: a.int2 :: HNil
  override def from(repr: Int :: Int :: HNil): InnerCombinedClass = InnerCombinedClass(repr.head, repr.tail.head)
}

object DeriveShow {
  import Show.mkShow
  implicit def hNilShow: Show[HNil] = mkShow(_ => "")
  implicit def hConsShow[H, T <: HList](implicit headShow: Show[H], tailShow: Show[T]): Show[H :: T] =
    mkShow(hCons => headShow.show(hCons.head) + " " + tailShow.show(hCons.tail))

  implicit def deriveShow[A, Repr](implicit gen: Generic.Aux[A, Repr], hListShow: Show[Repr]): Show[A] = mkShow(a => hListShow.show(gen.to(a)))
}

import DeriveShow._

Show[CombinedClass].show(CombinedClass(5, "hi"))
//Show[OuterCombinedClass].show(OuterCombinedClass("bye", InnerCombinedClass(1, 10)))
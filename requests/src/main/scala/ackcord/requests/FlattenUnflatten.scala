package ackcord.requests

import shapeless._

trait FlattenUnflatten[In, Out] {
  def toOut(in: In): Out
  def toIn(out: Out): In
}
object FlattenUnflatten extends LowPriorityFlatten {
  def apply[A, B](implicit flatten: FlattenUnflatten[A, B]): FlattenUnflatten[A, B] = flatten

  implicit def two[A, AO <: HList, B, BO <: HList, ALen <: Nat, Concatted <: HList](
      implicit aFlatten: FlattenUnflatten[A, AO],
      bFlatten: FlattenUnflatten[B, BO],
      lenA: ops.hlist.Length.Aux[AO, ALen],
      concat: ops.hlist.Prepend.Aux[AO, BO, Concatted],
      split: ops.hlist.Split.Aux[Concatted, ALen, AO, BO]
  ): FlattenUnflatten[(A, B), Concatted] = new FlattenUnflatten[(A, B), Concatted] {
    override def toOut(in: (A, B)): Concatted =
      concat(aFlatten.toOut(in._1), bFlatten.toOut(in._2))

    override def toIn(out: Concatted): (A, B) = {
      val (ao, bo) = split(out)
      (aFlatten.toIn(ao), bFlatten.toIn(bo))
    }
  }

  implicit val end: FlattenUnflatten[HNil, HNil] =
    new FlattenUnflatten[HNil, HNil] {
      override def toOut(in: HNil): HNil = HNil

      override def toIn(out: HNil): HNil = HNil
    }
}
trait LowPriorityFlatten {
  implicit def dontFlatten[A]: FlattenUnflatten[A, A :: HNil] = new FlattenUnflatten[A, A :: HNil] {
    override def toOut(in: A): A :: HNil = in :: HNil

    override def toIn(out: A :: HNil): A = out.head
  }
}

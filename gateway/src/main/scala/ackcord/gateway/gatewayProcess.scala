package ackcord.gateway

import ackcord.gateway.GatewayProcessComponent.FAlter
import ackcord.gateway.data.GatewayEventBase
import cats.syntax.all._
import cats.{Applicative, Functor, Monad, MonadError}
import org.typelevel.log4cats.Logger

trait GatewayProcess[F[_]] {

  def F: Applicative[F]

  def onCreateHandler(context: Context): F[Context] = F.pure(context)

  def onEvent(event: GatewayEventBase[_], context: Context): F[Context] = F.pure(context)

  def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] = F.pure(behavior)

  def name: String

  override def toString: String = name
}

trait GatewayProcessComponent[F[_]] { self =>
  def F: Monad[F]

  def name: String

  override def toString: String = name

  type Return[A]
  def makeReturn[A](value: A): Return[A]
  def makeReturnF[A](value: A): F[Return[A]] = F.pure(makeReturn(value))

  def valueFromReturn[A](ret: Return[A]): Option[A]

  def onCreateHandler(context: Context): F[Return[Context]] = makeReturnF(context)

  def onCreateHandlerAll(context: Context)(implicit alter: FAlter[F]): F[Context] = {
    implicit val m: Monad[F] = F
    alter
      .alter(onCreateHandler(context), makeReturn(context))
      .map(r => valueFromReturn(r).getOrElse(context))
      .flatMap(ctx => children.foldLeftM(ctx)((c, p) => p.onCreateHandlerAll(c)))
  }

  def onEvent(event: GatewayEventBase[_], context: Context): F[Return[Context]] = makeReturnF(context)

  def onEventAll(event: GatewayEventBase[_], context: Context)(implicit alter: FAlter[F]): F[Context] = {
    implicit val m: Monad[F] = F

    alter
      .alter(onEvent(event, context), makeReturn(context))
      .map(r => valueFromReturn(r).getOrElse(context))
      .flatMap(ctx => children.foldLeftM(ctx)((c, p) => p.onEventAll(event, c)))
  }

  def onDisconnected(behavior: DisconnectBehavior): F[Return[DisconnectBehavior]] = makeReturnF(behavior)

  def onDisconnectedAll(behavior: DisconnectBehavior)(implicit alter: FAlter[F]): F[DisconnectBehavior] = {
    implicit val m: Monad[F] = F
    alter
      .alter(onDisconnected(behavior), makeReturn(behavior))
      .map(r => valueFromReturn(r).getOrElse(behavior))
      .flatMap(ctx => children.foldLeftM(ctx)((b, p) => p.onDisconnectedAll(b)))
  }

  def children: Seq[GatewayProcessComponent[F]] = Nil
}
object GatewayProcessComponent {
  trait FAlter[F[_]] { self =>
    def alter[A](fa: F[A], default: A): F[A]

    def andThen(thenAlter: FAlter[F]): FAlter[F] = new FAlter[F] {
      override def alter[A](fa: F[A], default: A): F[A] =
        thenAlter.alter(self.alter(fa, default), default)
    }
  }
  object FAlter {
    def log[F[_]](log: Logger[F])(implicit F: MonadError[F, Throwable]): FAlter[F] = new FAlter[F] {
      override def alter[A](fa: F[A], default: A): F[A] = F.handleErrorWith(fa) { e =>
        F.as(log.error(e)("Encountered error while processing action"), default)
      }
    }

    def async[F[_]](implicit doAsync: DoAsync[F], F: Functor[F]): FAlter[F] = new FAlter[F] {
      override def alter[A](fa: F[A], default: A): F[A] = F.as(doAsync.async(F.void(fa)), default)
    }
  }
}

trait GatewayContextUpdater[F[_]] extends GatewayProcessComponent[F] {
  override type Return[A] = A
  override def makeReturn[A](value: A): A            = value
  override def valueFromReturn[A](ret: A): Option[A] = Some(ret)
}
object GatewayContextUpdater {
  abstract class Base[F[_]](val name: String)(implicit val F: Monad[F]) extends GatewayContextUpdater[F]
}

trait GatewayPureContextUpdater[F[_]] extends GatewayContextUpdater[F] { self =>
  override def onCreateHandler(context: Context): F[Return[Context]] =
    makeReturnF(onCreateHandlerUpdateContext(context))
  def onCreateHandlerUpdateContext(context: Context): Context = context

  override def onEvent(event: GatewayEventBase[_], context: Context): F[Return[Context]] =
    makeReturnF(onEventUpdateContext(event, context))
  def onEventUpdateContext(event: GatewayEventBase[_], context: Context): Context = context

  override def onDisconnected(behavior: DisconnectBehavior): F[Return[DisconnectBehavior]] =
    makeReturnF(onDisconnectedBehavior(behavior))
  def onDisconnectedBehavior(behavior: DisconnectBehavior): DisconnectBehavior = behavior

  override def onCreateHandlerAll(context: Context)(implicit alter: FAlter[F]): F[Context] = {
    implicit val m: Monad[F] = F
    alter
      .alter(onCreateHandler(context), makeReturn(onCreateHandlerUpdateContext(context)))
      .map(r => valueFromReturn(r).getOrElse(context))
      .flatMap(ctx => children.foldLeftM(ctx)((c, p) => p.onCreateHandlerAll(c)))
  }

  override def onEventAll(event: GatewayEventBase[_], context: Context)(implicit alter: FAlter[F]): F[Context] = {
    implicit val m: Monad[F] = F
    alter
      .alter(onEvent(event, context), makeReturn(onEventUpdateContext(event, context)))
      .map(r => valueFromReturn(r).getOrElse(context))
      .flatMap(ctx => children.foldLeftM(ctx)((c, p) => p.onEventAll(event, c)))
  }

  override def onDisconnectedAll(behavior: DisconnectBehavior)(implicit alter: FAlter[F]): F[DisconnectBehavior] = {
    implicit val m: Monad[F] = F
    alter
      .alter(onDisconnected(behavior), makeReturn(onDisconnectedBehavior(behavior)))
      .map(r => valueFromReturn(r).getOrElse(behavior))
      .flatMap(ctx => children.foldLeftM(ctx)((b, p) => p.onDisconnectedAll(b)))
  }
}
object GatewayPureContextUpdater {
  abstract class Base[F[_]](val name: String)(implicit val F: Monad[F]) extends GatewayPureContextUpdater[F]
}

trait GatewayProcessHandler[F[_]] extends GatewayProcessComponent[F] {
  override type Return[_] = Unit
  override def makeReturn[A](value: A): Unit            = ()
  override def valueFromReturn[A](ret: Unit): Option[A] = None
}
object GatewayProcessHandler {
  abstract class Base[F[_]](name0: String)(implicit val F: Monad[F]) extends GatewayProcessHandler[F] {
    val name: String = name0
  }
}

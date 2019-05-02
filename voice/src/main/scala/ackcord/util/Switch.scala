package ackcord.util

import java.util.concurrent.atomic.AtomicBoolean

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}

import scala.collection.immutable

//TODO: Maybe use a third inlet to determine where to listen to
class Switch[A](ref: AtomicBoolean, emitChangeTrue: immutable.Seq[A], emitChangeFalse: immutable.Seq[A])
    extends GraphStage[FanInShape2[A, A, A]] {
  override val shape: FanInShape2[A, A, A] = new FanInShape2[A, A, A]("Switch")

  val in1: Inlet[A]  = shape.in0
  val in2: Inlet[A]  = shape.in1
  val out: Outlet[A] = shape.out

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      private var lastState: Boolean = ref.get()
      private def activeIn: Inlet[A] = {
        val newState = ref.get()
        val newIn    = if (newState) in1 else in2

        if (lastState != newState) {
          lastState = newState

          emitMultiple(out, if (newState) emitChangeTrue else emitChangeFalse)
          emit(out, waitingOther)
          tryPull(newIn)
          waitingOther = null.asInstanceOf[A]
        }

        newIn
      }

      private var waitingOther: A = _

      private def setInHandler(in: Inlet[A]): Unit = {
        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit =
              if (activeIn == in) {
                emit(out, grab(in))
              } else {
                require(waitingOther == null, "Pushed other when a waiting other was already defined")
                waitingOther = grab(in)
              }
          }
        )
      }

      setInHandler(in1)
      setInHandler(in2)

      setHandler(out, this)

      override def onPull(): Unit = pull(activeIn)
    }
}

/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ackcord.requests

import scala.collection.immutable
import scala.concurrent.Future

import ackcord.{CacheSnapshot, RequestPermissionException}
import cats.data.OptionT
import cats.instances.future._

/**
  * A small layer on top of [[Requests]] for use in high level code.
  * @param requests The requests instance to use
  */
class RequestsHelper(requests: Requests) {
  import requests.system.executionContext
  implicit val properties: Requests.RequestProperties = Requests.RequestProperties.retry

  private def checkPerms(requests: Seq[Request[_]])(implicit c: CacheSnapshot): OptionT[Future, Unit] =
    if (requests.forall(_.hasPermissions)) OptionT.liftF(Future.unit)
    else OptionT.liftF(Future.failed(new RequestPermissionException(requests.find(!_.hasPermissions).get)))

  /**
    * Runs a single requests and returns the result.
    * @param request The request to run
    */
  def run[A](request: Request[A])(implicit c: CacheSnapshot): OptionT[Future, A] =
    checkPerms(Seq(request)).semiflatMap(_ => requests.singleFuture(request)).semiflatMap {
      case RequestResponse(data, _, _, _) => Future.successful(data)
      case request: FailedRequest         => Future.failed(request.asException)
    }

  /**
    * Runs many requests in order, and returns the result. The result is only
    * a success if all the requests succeed.
    * @param requests The requests to run
    */
  def runMany[A](requests: Request[A]*)(implicit c: CacheSnapshot): OptionT[Future, immutable.Seq[A]] =
    checkPerms(requests).semiflatMap(_ => this.requests.manyFutureSuccess(immutable.Seq(requests: _*)))

  //From here on it's all convenience methods

  def pure[A](a: A): OptionT[Future, A] = OptionT.some[Future](a)

  def optionPure[A](opt: Option[A]): OptionT[Future, A] = OptionT.fromOption[Future](opt)

  def runOption[A](opt: Option[Request[A]])(implicit c: CacheSnapshot): OptionT[Future, A] =
    optionPure(opt).flatMap(run)

  def runOptionT[H[_], A](
      opt: OptionT[Future, Request[A]]
  )(implicit c: CacheSnapshot): OptionT[Future, A] =
    opt.flatMap(run)
}

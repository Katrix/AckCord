/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.http.requests

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, RequestEntity, ResponseEntity, Uri}
import akka.stream.Materializer

/**
  * Used by requests for specifying an uri to send to,
  * together with a method to use.
  * @param uri The uri to send to
  * @param method The method to use
  */
case class RequestRoute(uri: Uri, method: HttpMethod)


/**
  * Base super simple trait for all HTTP requests in AckCord.
  * @tparam Response The parsed response type.
  */
trait Request[+Response] {

  /**
    * The router for this request.
    */
  def route: RequestRoute

  /**
    * The body of the request to send.
    */
  def requestBody: RequestEntity

  /**
    * Parses the response from this request.
    * @param responseEntity The response entity.
    */
  def parseResponse(
      responseEntity: ResponseEntity
  )(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[Response]
}
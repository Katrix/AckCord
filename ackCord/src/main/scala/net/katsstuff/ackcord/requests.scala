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
package net.katsstuff.ackcord

import akka.actor.ActorRef

/**
  * Sent as a response from a [[Request]].
  * @tparam Context The context type.
  */
trait RequestAnswer[Context] {

  /**
    * The context sent with the request.
    */
  def context: Context
}

/**
  * Sent when a [[Request]] succeeds.
  * @param data The received data.
  * @param context The context sent with the request.
  * @tparam Data The received data type.
  * @tparam Context The context type.
  */
case class RequestResponse[Data, Context](data: Data, context: Context) extends RequestAnswer[Context]

/**
  * Trait for all the types of failed requests.
  */
sealed trait RequestFailed[Context] extends RequestAnswer[Context]

/**
  * Send if a request was rate limited.
  */
case class RequestRatelimited[Context](context: Context) extends RequestFailed[Context]

/**
  * Sent when a [[Request]] encounters an error.
  * @param e The error that failed this request.
  * @param context The context sent with the request.
  * @tparam E The error type.
  * @tparam Context The context type.
  */
case class RequestError[E <: Throwable, Context](e: E, context: Context) extends RequestFailed[Context]

/**
  * Used to wrap a request in such a way that the handler know who to respond to.
  * @param request The request object.
  * @param context The data to send with the request.
  * @param sendResponseTo The actor to send the reply to in the form of [[RequestAnswer]].
  * @param retries The amount of times to retry this request if rate limited.
  * @tparam RequestTpe The request type.
  * @tparam Context The context type.
  */
case class Request[RequestTpe, Context](request: RequestTpe, context: Context, sendResponseTo: ActorRef, retries: Int = 0) {

  /**
    * Set the amount of times to retry this request if rate limited.
    */
  def withRetry(num: Int): Request[RequestTpe, Context] = this.copy(retries = num)
}
/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord.interactions

import ackcord.data.{SnowflakeType, Webhook}

sealed trait AsyncToken {
  def webhookId: SnowflakeType[Webhook]
  def webhookToken: String
}
sealed trait AsyncMessageToken extends AsyncToken

object AsyncToken {
  private[interactions] case class Impl(webhookId: SnowflakeType[Webhook], webhookToken: String)
      extends AsyncMessageToken

  private[interactions] def fromInteraction(interaction: Interaction): AsyncToken =
    Impl(interaction.webhookId, interaction.token)

  private[interactions] def fromInteractionWithMessage(interaction: Interaction): AsyncMessageToken =
    Impl(interaction.webhookId, interaction.token)
}

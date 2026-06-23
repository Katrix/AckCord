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
package ackcord

import ackcord.data._
import io.circe.parser
import io.circe.syntax._
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
  * Regression tests for Discord's Components V2 (the layout/content components such
  * as Container, Section and Media Gallery that can appear at the top level of a
  * message when the IS_COMPONENTS_V2 flag is set).
  */
class ComponentsV2Spec extends AnyFunSuite with Matchers with OptionValues {

  private object Protocol extends DiscordProtocol
  import Protocol._

  private def decodeComponents(json: String): Seq[TopLevelComponent] =
    parser.decode[Seq[TopLevelComponent]](json).fold(throw _, identity)

  test("a top level Container with more than five children decodes (no longer 'Too many components in ActionRow')") {
    val json =
      """[
        |  {
        |    "type": 17,
        |    "accent_color": 5814783,
        |    "spoiler": false,
        |    "components": [
        |      {"type": 10, "content": "1"},
        |      {"type": 10, "content": "2"},
        |      {"type": 10, "content": "3"},
        |      {"type": 10, "content": "4"},
        |      {"type": 10, "content": "5"},
        |      {"type": 10, "content": "6"},
        |      {"type": 10, "content": "7"}
        |    ]
        |  }
        |]""".stripMargin

    decodeComponents(json) match {
      case Seq(container: Container) =>
        container.tpe shouldBe ComponentType.Container
        container.accentColor shouldBe Some(5814783)
        container.components should have size 7
        container.components.foreach(_ shouldBe a[TextDisplay])
      case other => fail(s"Expected a single Container, got $other")
    }
  }

  test("a Media Gallery decodes from 'items' (no longer 'Missing required field: media')") {
    val json =
      """[
        |  {
        |    "type": 12,
        |    "items": [
        |      {"media": {"url": "https://example.com/a.png"}, "description": "alt", "spoiler": false},
        |      {"media": {"url": "https://example.com/b.png"}}
        |    ]
        |  }
        |]""".stripMargin

    decodeComponents(json) match {
      case Seq(gallery: MediaGallery) =>
        gallery.items should have size 2
        gallery.items.head.media.url shouldBe "https://example.com/a.png"
        gallery.items.head.description shouldBe Some("alt")
      case other => fail(s"Expected a single MediaGallery, got $other")
    }
  }

  test("a Section (type 9) decodes with its text children and accessory") {
    val json =
      """[
        |  {
        |    "type": 9,
        |    "components": [
        |      {"type": 10, "content": "# Title"},
        |      {"type": 10, "content": "body"}
        |    ],
        |    "accessory": {"type": 11, "media": {"url": "https://example.com/thumb.webp"}}
        |  }
        |]""".stripMargin

    decodeComponents(json) match {
      case Seq(section: Section) =>
        section.components should have size 2
        section.accessory shouldBe a[Thumbnail]
      case other => fail(s"Expected a single Section, got $other")
    }
  }

  test("a Container nesting a Section, Media Gallery and Action Row decodes (the real nested failure)") {
    val json =
      """[
        |  {
        |    "type": 17,
        |    "components": [
        |      {"type": 10, "content": "hi"},
        |      {
        |        "type": 9,
        |        "components": [{"type": 10, "content": "x"}],
        |        "accessory": {"type": 2, "style": 2, "custom_id": "b", "label": "L"}
        |      },
        |      {"type": 12, "items": [{"media": {"url": "https://example.com/a.png"}}]},
        |      {"type": 1, "components": [{"type": 2, "style": 1, "custom_id": "c", "label": "Click"}]}
        |    ]
        |  }
        |]""".stripMargin

    val container = decodeComponents(json) match {
      case Seq(c: Container) => c
      case other             => fail(s"Expected a single Container, got $other")
    }

    container.components.map(_.tpe) shouldBe Seq(
      ComponentType.TextDisplay,
      ComponentType.Section,
      ComponentType.MediaGallery,
      ComponentType.ActionRow
    )

    val section = container.components.collectFirst { case s: Section => s }.value
    section.accessory shouldBe a[Button]

    val actionRow = container.components.collectFirst { case ar: ActionRow => ar }.value
    actionRow.components.head shouldBe a[TextButton]
  }

  test("plain v1 messages (top level Action Rows) still decode") {
    val json =
      """[
        |  {"type": 1, "components": [{"type": 2, "style": 1, "custom_id": "c", "label": "Click"}]}
        |]""".stripMargin

    decodeComponents(json) match {
      case Seq(actionRow: ActionRow) =>
        actionRow.components should have size 1
        actionRow.components.head shouldBe a[TextButton]
      case other => fail(s"Expected a single ActionRow, got $other")
    }
  }

  test("v2 components round-trip through the encoder and back") {
    val json =
      """[
        |  {
        |    "type": 17,
        |    "components": [
        |      {"type": 10, "content": "hi"},
        |      {"type": 14, "divider": true, "spacing": 1},
        |      {"type": 12, "items": [{"media": {"url": "https://example.com/a.png"}}]}
        |    ]
        |  }
        |]""".stripMargin

    val decoded   = decodeComponents(json)
    val reDecoded = decodeComponents(decoded.asJson.noSpaces)
    reDecoded shouldBe decoded
  }

  test("a Thumbnail's media decodes the fields needed to render it") {
    val json =
      """{
        |  "type": 11,
        |  "spoiler": true,
        |  "media": {
        |    "url": "https://example.com/a.png",
        |    "proxy_url": "https://proxy/a.png",
        |    "height": 100,
        |    "width": 200,
        |    "content_type": "image/png"
        |  }
        |}""".stripMargin

    val thumbnail = parser.decode[Thumbnail](json).fold(throw _, identity)
    thumbnail.spoiler shouldBe Some(true)

    val item = thumbnail.media
    item.url shouldBe "https://example.com/a.png"
    item.proxyUrl shouldBe Some("https://proxy/a.png")
    item.height shouldBe Some(100)
    item.width shouldBe Some(200)
    item.contentType shouldBe Some("image/png")
  }
}

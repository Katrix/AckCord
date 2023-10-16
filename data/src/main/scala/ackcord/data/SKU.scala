//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/SKU.yaml

import ackcord.data.base._
import io.circe.Json

/**
  * SKUs (stock-keeping units) in Discord represent premium offerings that can
  * be made available to your application's users or guilds.
  */
class SKU(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** ID of SKU */
  @inline def id: Snowflake[SKU] = selectDynamic[Snowflake[SKU]]("id")

  @inline def withId(newValue: Snowflake[SKU]): SKU = objWith(SKU, "id", newValue)

  /** Type of SKU */
  @inline def tpe: SKU.SKUType = selectDynamic[SKU.SKUType]("type")

  @inline def withTpe(newValue: SKU.SKUType): SKU = objWith(SKU, "type", newValue)

  /** ID of the parent application */
  @inline def applicationId: ApplicationId = selectDynamic[ApplicationId]("application_id")

  @inline def withApplicationId(newValue: ApplicationId): SKU = objWith(SKU, "application_id", newValue)

  /** Customer-facing name of your premium offering */
  @inline def name: String = selectDynamic[String]("name")

  @inline def withName(newValue: String): SKU = objWith(SKU, "name", newValue)

  /** System-generated URL slug based on the SKU's name */
  @inline def slug: String = selectDynamic[String]("slug")

  @inline def withSlug(newValue: String): SKU = objWith(SKU, "slug", newValue)

  /** SKU flags combined as a bitfield */
  @inline def flags: SKU.Flags = selectDynamic[SKU.Flags]("flags")

  @inline def withFlags(newValue: SKU.Flags): SKU = objWith(SKU, "flags", newValue)

  override def values: Seq[() => Any] =
    Seq(() => id, () => tpe, () => applicationId, () => name, () => slug, () => flags)
}
object SKU extends DiscordObjectCompanion[SKU] {
  def makeRaw(json: Json, cache: Map[String, Any]): SKU = new SKU(json, cache)

  /**
    * @param id
    *   ID of SKU
    * @param tpe
    *   Type of SKU
    * @param applicationId
    *   ID of the parent application
    * @param name
    *   Customer-facing name of your premium offering
    * @param slug
    *   System-generated URL slug based on the SKU's name
    * @param flags
    *   SKU flags combined as a bitfield
    */
  def make20(
      id: Snowflake[SKU],
      tpe: SKU.SKUType,
      applicationId: ApplicationId,
      name: String,
      slug: String,
      flags: SKU.Flags
  ): SKU = makeRawFromFields(
    "id"             := id,
    "type"           := tpe,
    "application_id" := applicationId,
    "name"           := name,
    "slug"           := slug,
    "flags"          := flags
  )

  /**
    * For subscriptions, SKUs will have a type of either SUBSCRIPTION
    * represented by type: 5 or SUBSCRIPTION_GROUP represented by type:6. For
    * any current implementations, you will want to use the SKU defined by type:
    * 5. A SUBSCRIPTION_GROUP is automatically created for each SUBSCRIPTION SKU
    * and are not used at this time.
    */
  sealed case class SKUType private (value: Int) extends DiscordEnum[Int]
  object SKUType                                 extends DiscordEnumCompanion[Int, SKUType] {

    /** Represents a recurring subscription */
    val SUBSCRIPTION: SKUType = SKUType(5)

    /** System-generated group for each SUBSCRIPTION SKU created */
    val SUBSCRIPTION_GROUP: SKUType = SKUType(6)

    def unknown(value: Int): SKUType = new SKUType(value)

    val values: Seq[SKUType] = Seq(SUBSCRIPTION, SUBSCRIPTION_GROUP)
  }

  /**
    * For subscriptions, there are two types of access levels you can offer to
    * users: The flags field can be used to differentiate user and server
    * subscriptions with a bitwise && operator.
    */
  sealed case class Flags private (value: Int) extends DiscordEnum[Int]
  object Flags extends DiscordEnumCompanion[Int, Flags] {
    val GUILD_SUBSCRIPTION: Flags = Flags(1 << 7)

    val USER_SUBSCRIPTION: Flags = Flags(1 << 8)

    def unknown(value: Int): Flags = new Flags(value)

    val values: Seq[Flags] = Seq(GUILD_SUBSCRIPTION, USER_SUBSCRIPTION)

    implicit class FlagsBitFieldOps(private val here: Flags) extends AnyVal {

      def toInt: Int = here.value

      def ++(there: Flags): Flags = Flags(here.value | there.value)

      def --(there: Flags): Flags = Flags(here.value & ~there.value)

      def isNone: Boolean = here.value == 0
    }
  }
}

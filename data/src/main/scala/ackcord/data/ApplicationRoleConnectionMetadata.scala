//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/ApplicationRoleConnectionMetadata.yaml

import ackcord.data.base._
import io.circe.Json

class ApplicationRoleConnectionMetadata(json: Json, cache: Map[String, Any] = Map.empty)
    extends DiscordObject(json, cache) {

  /** Type of metadata value */
  @inline def tpe: ApplicationRoleConnectionMetadata.ApplicationRoleConnectionMetadataType =
    selectDynamic[ApplicationRoleConnectionMetadata.ApplicationRoleConnectionMetadataType]("type")

  /**
    * Dictionary key for the metadata field (must be a-z, 0-9, or _ characters;
    * 1-50 characters)
    */
  @inline def key: String = selectDynamic[String]("key")

  /** Name of the metadata field (1-100 characters) */
  @inline def name: String = selectDynamic[String]("name")

  /** Translations of the name */
  @inline def nameLocalizations: UndefOr[Map[String, String]] =
    selectDynamic[UndefOr[Map[String, String]]]("name_localizations")

  /** Description of the metadata field (1-200 characters) */
  @inline def description: String = selectDynamic[String]("description")

  /** Translations of the description */
  @inline def descriptionLocalizations: UndefOr[Map[String, String]] =
    selectDynamic[UndefOr[Map[String, String]]]("description_localizations")

  override def values: Seq[() => Any] =
    Seq(() => tpe, () => key, () => name, () => nameLocalizations, () => description, () => descriptionLocalizations)
}
object ApplicationRoleConnectionMetadata extends DiscordObjectCompanion[ApplicationRoleConnectionMetadata] {
  def makeRaw(json: Json, cache: Map[String, Any]): ApplicationRoleConnectionMetadata =
    new ApplicationRoleConnectionMetadata(json, cache)

  /**
    * @param tpe
    *   Type of metadata value
    * @param key
    *   Dictionary key for the metadata field (must be a-z, 0-9, or _
    *   characters; 1-50 characters)
    * @param name
    *   Name of the metadata field (1-100 characters)
    * @param nameLocalizations
    *   Translations of the name
    * @param description
    *   Description of the metadata field (1-200 characters)
    * @param descriptionLocalizations
    *   Translations of the description
    */
  def make20(
      tpe: ApplicationRoleConnectionMetadata.ApplicationRoleConnectionMetadataType,
      key: String,
      name: String,
      nameLocalizations: UndefOr[Map[String, String]] = UndefOrUndefined,
      description: String,
      descriptionLocalizations: UndefOr[Map[String, String]] = UndefOrUndefined
  ): ApplicationRoleConnectionMetadata = makeRawFromFields(
    "type"                       := tpe,
    "key"                        := key,
    "name"                       := name,
    "name_localizations"        :=? nameLocalizations,
    "description"                := description,
    "description_localizations" :=? descriptionLocalizations
  )

  sealed case class ApplicationRoleConnectionMetadataType private (value: Int) extends DiscordEnum[Int]
  object ApplicationRoleConnectionMetadataType
      extends DiscordEnumCompanion[Int, ApplicationRoleConnectionMetadataType] {

    /**
      * The metadata value (integer) is less than or equal to the guild's
      * configured value (integer)
      */
    val INTEGER_LESS_THAN_OR_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(1)

    /**
      * The metadata value (integer) is greater than or equal to the guild's
      * configured value (integer)
      */
    val INTEGER_GREATER_THAN_OR_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(2)

    /**
      * The metadata value (integer) is equal to the guild's configured value
      * (integer)
      */
    val INTEGER_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(3)

    /**
      * The metadata value (integer) is not equal to the guild's configured
      * value (integer)
      */
    val INTEGER_NOT_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(4)

    /**
      * The metadata value (ISO8601 string) is less than or equal to the guild's
      * configured value (integer; days before current date)
      */
    val DATETIME_LESS_THAN_OR_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(5)

    /**
      * The metadata value (ISO8601 string) is greater than or equal to the
      * guild's configured value (integer; days before current date)
      */
    val DATETIME_GREATER_THAN_OR_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(6)

    /**
      * The metadata value (integer) is equal to the guild's configured value
      * (integer; 1)
      */
    val BOOLEAN_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(7)

    /**
      * The metadata value (integer) is not equal to the guild's configured
      * value (integer; 1)
      */
    val BOOLEAN_NOT_EQUAL: ApplicationRoleConnectionMetadataType = ApplicationRoleConnectionMetadataType(8)

    def unknown(value: Int): ApplicationRoleConnectionMetadataType = new ApplicationRoleConnectionMetadataType(value)

    def values: Seq[ApplicationRoleConnectionMetadataType] = Seq(
      INTEGER_LESS_THAN_OR_EQUAL,
      INTEGER_GREATER_THAN_OR_EQUAL,
      INTEGER_EQUAL,
      INTEGER_NOT_EQUAL,
      DATETIME_LESS_THAN_OR_EQUAL,
      DATETIME_GREATER_THAN_OR_EQUAL,
      BOOLEAN_EQUAL,
      BOOLEAN_NOT_EQUAL
    )

  }
}

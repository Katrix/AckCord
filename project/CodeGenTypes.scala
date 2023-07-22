import scala.collection.immutable.ListMap

import io.circe._

object CodeGenTypes {

  case class AnonymousClassTypeDef(
      imports: Seq[String],
      documentation: Option[String],
      innerTypes: Seq[TypeDef],
      allUndefined: Boolean,
      fields: ListMap[String, ListMap[String, FieldDef]],
      `extends`: Seq[String],
      objectExtends: Seq[String]
  ) {
    def named(name: String): TypeDef.ClassTypeDef = TypeDef.ClassTypeDef(name, this)

    def mapFields(f: FieldDef => FieldDef): AnonymousClassTypeDef =
      copy(fields = fields.map(t1 => t1._1 -> t1._2.map(t2 => t2._1 -> f(t2._2))))
  }

  object AnonymousClassTypeDef {
    implicit lazy val typeDefDecoder: Decoder[AnonymousClassTypeDef] = (c: HCursor) => {
      implicit val fieldDefOrTypeDecoder: Decoder[Either[String, FieldDef]] =
        (c: HCursor) =>
          c.as[String] match {
            case Left(_)      => c.as[FieldDef].map(Right(_))
            case Right(value) => Right(Left(value))
          }

      for {
        imports       <- c.getOrElse[Seq[String]]("imports")(Nil)
        documentation <- c.get[Option[String]]("documentation")
        innerTypes    <- c.getOrElse[Seq[TypeDef]]("innerTypes")(Nil)
        allUndefined  <- c.getOrElse[Boolean]("allUndefined")(false)
        fieldsMap     <- c.get[ListMap[String, ListMap[String, Either[String, FieldDef]]]]("fields")
        extend        <- c.getOrElse[Seq[String]]("extends")(Nil)
        objectExtends <- c.getOrElse[Seq[String]]("objectExtends")(Nil)
      } yield AnonymousClassTypeDef(
        imports,
        documentation,
        innerTypes,
        allUndefined,
        fieldsMap.map { case (k1, v1) =>
          k1 -> v1.map { case (k2, v2) =>
            k2 -> v2.swap
              .map(tpe => FieldDef(tpe, None, None, None, withUndefined = false, withNull = false, None))
              .merge
          }
        },
        extend,
        objectExtends
      )
    }
  }

  sealed trait AnonymousClassTypeDefOrType

  object AnonymousClassTypeDefOrType {
    case class TypeRef(name: String) extends AnonymousClassTypeDefOrType

    case class AnonType(anon: AnonymousClassTypeDef) extends AnonymousClassTypeDefOrType

    implicit lazy val decoder: Decoder[AnonymousClassTypeDefOrType] = (c: HCursor) =>
      c.as[String].map(TypeRef).swap.map(_ => c.as[AnonymousClassTypeDef].map(AnonType)).swap.joinLeft
  }

  trait TypeDef {
    def imports: Seq[String]
  }

  object TypeDef {
    case class ClassTypeDef(
        name: String,
        anonPart: AnonymousClassTypeDef
    ) extends TypeDef {
      override def imports: Seq[String] = anonPart.imports
    }

    case class EnumTypeDef(
        name: String,
        tpe: String,
        isBitField: Boolean,
        imports: Seq[String],
        documentation: Option[String],
        innerTypes: Seq[TypeDef],
        values: ListMap[String, EnumValue],
        objectExtends: Seq[String]
    ) extends TypeDef

    case class OpaqueTypeDef(
        name: String,
        imports: Seq[String],
        documentation: Option[String],
        underlying: String,
        includeAlias: Boolean,
        innerTypes: Seq[TypeDef],
        objectExtends: Seq[String]
    ) extends TypeDef

    case class RequestDef(
        name: String,
        imports: Seq[String],
        documentation: Option[String],
        path: Seq[PathElem],
        method: String,
        query: Option[AnonymousClassTypeDef],
        arrayOfBody: Boolean,
        body: Option[AnonymousClassTypeDefOrType],
        arrayOfReturn: Boolean,
        returnTpe: Option[AnonymousClassTypeDefOrType],
        allowsReason: Boolean,
        additionalTypeParams: Seq[String],
        additionalParams: Map[String, RequestDefAdditionalParam],
        complexType: RequestDefComplexType,
        encodeBody: Option[String],
        parseResponse: Option[String]
    ) extends TypeDef

    case class MultipleDefs(
        imports: Seq[String],
        innerTypes: Seq[TypeDef]
    ) extends TypeDef

    case class ObjectOnlyDef(
        name: String,
        imports: Seq[String],
        innerTypes: Seq[TypeDef],
        objectExtends: Seq[String]
    ) extends TypeDef

    case class FreeformDef(
        content: String,
        imports: Seq[String],
        documentation: Option[String]
    ) extends TypeDef

    implicit lazy val typeDefDecoder: Decoder[TypeDef] = (c: HCursor) =>
      for {
        imports       <- c.getOrElse[Seq[String]]("imports")(Nil)
        documentation <- c.get[Option[String]]("documentation")
        innerTypes    <- c.getOrElse[Seq[TypeDef]]("innerTypes")(Nil)
        defType       <- c.get[String]("defType")
        res <- defType match {
          case "Class" =>
            for {
              name     <- c.get[String]("name")
              anonPart <- c.as[AnonymousClassTypeDef]
            } yield ClassTypeDef(
              name,
              anonPart
            )

          case "Enum" =>
            for {
              name          <- c.get[String]("name")
              tpe           <- c.get[String]("type")
              isBitfield    <- c.getOrElse[Boolean]("isBitfield")(false)
              values        <- c.get[ListMap[String, EnumValue]]("values")
              objectExtends <- c.getOrElse[Seq[String]]("objectExtends")(Nil)
            } yield EnumTypeDef(name, tpe, isBitfield, imports, documentation, innerTypes, values, objectExtends)

          case "Opaque" =>
            for {
              name          <- c.get[String]("name")
              underlying    <- c.get[String]("underlying")
              includeAlias  <- c.getOrElse[Boolean]("includeAlias")(true)
              objectExtends <- c.getOrElse[Seq[String]]("objectExtends")(Nil)
            } yield OpaqueTypeDef(name, imports, documentation, underlying, includeAlias, innerTypes, objectExtends)

          case "Request" =>
            for {
              name                 <- c.get[String]("name")
              path                 <- c.get[Seq[PathElem]]("path")
              method               <- c.get[String]("method")
              query                <- c.get[Option[AnonymousClassTypeDef]]("query")
              arrayOfBody          <- c.getOrElse[Boolean]("arrayOfBody")(false)
              body                 <- c.get[Option[AnonymousClassTypeDefOrType]]("body")
              arrayOfReturn        <- c.getOrElse[Boolean]("arrayOfReturn")(false)
              returnTpe            <- c.get[Option[AnonymousClassTypeDefOrType]]("return")
              allowsReason         <- c.getOrElse[Boolean]("allowsReason")(false)
              additionalTypeParams <- c.getOrElse[Seq[String]]("additionalTypeParams")(Nil)
              additionalParams     <- c.getOrElse[Map[String, RequestDefAdditionalParam]]("additionalParams")(Map.empty)
              complexType   <- c.getOrElse[RequestDefComplexType]("complexType")(RequestDefComplexType(None, None))
              encodeBody    <- c.get[Option[String]]("encodeBody")
              parseResponse <- c.get[Option[String]]("parseResponse")
            } yield RequestDef(
              name,
              imports,
              documentation,
              path,
              method,
              query,
              arrayOfBody,
              body,
              arrayOfReturn,
              returnTpe,
              allowsReason,
              additionalTypeParams,
              additionalParams,
              complexType,
              encodeBody,
              parseResponse
            )

          case "Multiple" =>
            Right(MultipleDefs(imports, innerTypes))

          case "ObjectOnly" =>
            for {
              name          <- c.get[String]("name")
              objectExtends <- c.getOrElse[Seq[String]]("objectExtends")(Nil)
            } yield ObjectOnlyDef(name, imports, innerTypes, objectExtends)

          case "Freeform" =>
            for {
              content <- c.get[String]("content")
            } yield FreeformDef(content, imports, documentation)
        }
      } yield res
  }

  case class FieldDef(
      tpe: String,
      jsonName: Option[String],
      default: Option[String],
      documentation: Option[String],
      withUndefined: Boolean,
      withNull: Boolean,
      isExtension: Boolean,
      verification: Option[FieldVerification]
  )

  object FieldDef {

    implicit lazy val fieldDefDecoder: Decoder[FieldDef] = (c: HCursor) =>
      for {
        tpe           <- c.get[String]("type")
        jsonName      <- c.get[Option[String]]("jsonName")
        default       <- c.get[Option[String]]("default")
        documentation <- c.get[Option[String]]("documentation")
        withUndefined <- c.getOrElse("withUndefined")(false)
        withNull      <- c.getOrElse("withNull")(false)
        isExtension   <- c.getOrElse("isExtension")(false)
        verification  <- c.get[Option[FieldVerification]]("verification")
      } yield FieldDef(tpe, jsonName, default, documentation, withUndefined, withNull, isExtension, verification)
  }

  case class FieldVerification(
      minLength: Option[Int],
      maxLength: Option[Int]
  )

  object FieldVerification {
    implicit lazy val fieldVerificationDecoder: Decoder[FieldVerification] = (c: HCursor) =>
      for {
        minLength <- c.get[Option[Int]]("minLength")
        maxLength <- c.get[Option[Int]]("maxLength")
      } yield FieldVerification(minLength, maxLength)
  }

  sealed trait PathElem

  object PathElem {
    case class StringPathElem(elem: String) extends PathElem

    case class ArgPathElem(name: Option[String], argOf: String, documentation: Option[String]) extends PathElem

    case class CustomArgPathElem(
        name: String,
        tpe: String,
        majorParameter: Boolean,
        documentation: Option[String]
    ) extends PathElem

    implicit lazy val pathElemDecoder: Decoder[PathElem] = (c: HCursor) =>
      c.as[String]
        .map[PathElem](StringPathElem)
        .swap
        .map[Either[DecodingFailure, PathElem]] { _ =>
          for {
            name          <- c.get[Option[String]]("name")
            argOf         <- c.get[String]("argOf")
            documentation <- c.get[Option[String]]("documentation")
          } yield ArgPathElem(name, argOf, documentation)
        }
        .swap
        .joinLeft
        .swap
        .map { _ =>
          for {
            name          <- c.get[String]("name")
            tpe           <- c.get[String]("customArgType")
            majorParam    <- c.getOrElse[Boolean]("customArgMajorParameter")(false)
            documentation <- c.get[Option[String]]("documentation")
          } yield CustomArgPathElem(name, tpe, majorParam, documentation)
        }
        .swap
        .joinLeft
  }

  case class RequestDefAdditionalParam(tpe: String, default: Option[String])
  object RequestDefAdditionalParam {
    implicit lazy val requestDefAdditionalParamsDecoder: Decoder[RequestDefAdditionalParam] = (c: HCursor) =>
      c.as[String]
        .map(RequestDefAdditionalParam(_, None))
        .swap
        .map[Either[DecodingFailure, RequestDefAdditionalParam]] { _ =>
          for {
            tpe     <- c.get[String]("type")
            default <- c.get[Option[String]]("default")
          } yield RequestDefAdditionalParam(tpe, default)
        }
        .swap
        .joinLeft
  }

  case class RequestDefComplexType(r1: String, r2: String) {
    def isEmpty: Boolean = r1 == "Any" && r2 == "Any"
  }
  object RequestDefComplexType {
    implicit lazy val requestDefComplexTypeDecoder: Decoder[RequestDefComplexType] = (c: HCursor) =>
      for {
        r1 <- c.getOrElse[String]("R1")("Any")
        r2 <- c.getOrElse[String]("R2")("Any")
      } yield RequestDefComplexType(r1, r2)
  }

  case class EnumValue(value: String, documentation: Option[String])
  object EnumValue {
    implicit lazy val enumValueDecoder: Decoder[EnumValue] = (c: HCursor) =>
      c.as[String]
        .map(EnumValue(_, None))
        .swap
        .map[Either[DecodingFailure, EnumValue]] { _ =>
          for {
            value         <- c.get[String]("value")
            documentation <- c.get[Option[String]]("documentation")
          } yield EnumValue(value, documentation)
        }
        .swap
        .joinLeft
  }
}

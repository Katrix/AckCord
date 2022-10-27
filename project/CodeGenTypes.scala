import scala.collection.immutable.ListMap

import io.circe._

object CodeGenTypes {

  case class AnonymousClassTypeDef(
      imports: Seq[String],
      documentation: Option[String],
      innerTypes: Seq[TypeDef],
      fields: ListMap[String, ListMap[String, FieldDef]]
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
        fieldsMap     <- c.get[ListMap[String, ListMap[String, Either[String, FieldDef]]]]("fields")
      } yield AnonymousClassTypeDef(
        imports,
        documentation,
        innerTypes,
        fieldsMap.map { case (k1, v1) =>
          k1 -> v1.map { case (k2, v2) =>
            k2 -> v2.swap.map(tpe => FieldDef(tpe, None, None, withUndefined = false, withNull = false, None)).merge
          }
        }
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
        imports: Seq[String],
        enumType: String,
        documentation: Option[String],
        innerTypes: Seq[TypeDef],
        values: ListMap[String, String]
    ) extends TypeDef

    case class MultipleDefs(
        imports: Seq[String],
        innerTypes: Seq[TypeDef]
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

          case enumType @ ("IntEnum" | "StringEnum") =>
            for {
              name   <- c.get[String]("name")
              values <- c.get[ListMap[String, String]]("values")
            } yield EnumTypeDef(name, imports, enumType, documentation, innerTypes, values)

          case "Multiple" =>
            Right(MultipleDefs(imports, innerTypes))
        }
      } yield res
  }

  case class FieldDef(
      tpe: String,
      default: Option[String],
      documentation: Option[String],
      withUndefined: Boolean,
      withNull: Boolean,
      verification: Option[FieldVerification]
  )

  object FieldDef {

    implicit lazy val fieldDefDecoder: Decoder[FieldDef] = (c: HCursor) =>
      for {
        tpe           <- c.get[String]("type")
        default       <- c.get[Option[String]]("default")
        documentation <- c.get[Option[String]]("documentation")
        withUndefined <- c.getOrElse("withUndefined")(false)
        withNull      <- c.getOrElse("withNull")(false)
        verification  <- c.get[Option[FieldVerification]]("verification")
      } yield FieldDef(tpe, default, documentation, withUndefined, withNull, verification)
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
}

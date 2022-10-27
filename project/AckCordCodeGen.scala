import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._

import CodeGenTypes._
import io.circe._

object AckCordCodeGen {

  def generateCodeFromFile(generatedRoot: Path, yamlFile: Path): String = {
    val relativeYamlPath = generatedRoot.relativize(yamlFile).iterator.asScala.map(_.toString).toList.init

    val typeDef =
      yaml.parser.parse(Files.readAllLines(yamlFile).asScala.mkString("\n")).flatMap(_.as[TypeDef]).toTry.get

    val packagePath     = relativeYamlPath.mkString(".")
    val packageLine     = s"package $packagePath"
    val code            = codeFromTypeDef(typeDef)
    val codeWithPackage = packageLine :: code

    codeWithPackage.mkString("\n\n")
  }

  def codeFromTypeDef(typeDef: TypeDef): List[String] = {
    val imports = typeDef.imports.map(s => s"import $s")

    val res = typeDef match {
      case classTypeDef: TypeDef.ClassTypeDef => List(codeFromClassTypeDef(classTypeDef))
      case enumTypeDef: TypeDef.EnumTypeDef   => List(codeFromEnumTypeDef(enumTypeDef))
      case requestDef: TypeDef.RequestDef     => codeFromRequestDef(requestDef)
      case multiple: TypeDef.MultipleDefs     => multiple.innerTypes.toList.flatMap(codeFromTypeDef)
    }

    imports.mkString("\n") :: res
  }

  def camelCase(s: String): String = {
    val arr = s.split("_")
    arr.head + arr.iterator.drop(1).map(_.capitalize).mkString
  }

  def docString(s: String, extra: Seq[String] = Nil): String = {
    val lines         = s.linesIterator.toList
    val linesWithStar = if (lines.size <= 1) lines.mkString else lines.map("* " + _).mkString("\n")
    val extraWithStar = extra.map("*" + _).mkString("\n")
    s"/** $linesWithStar\n$extraWithStar\n */"
  }

  def codeFromClassTypeDef(classTypeDef: TypeDef.ClassTypeDef): String = {
    val tpeName = classTypeDef.name

    val fieldsWithTypes = classTypeDef.anonPart.fields.map { case (version, fields) =>
      version -> fields.map { case (name, field) =>
        val fieldType = (field.withUndefined, field.withNull) match {
          case (true, true)   => s"JsonOption[${field.tpe}]"
          case (true, false)  => s"UndefOr[${field.tpe}]"
          case (false, true)  => s"Option[${field.tpe}]"
          case (false, false) => field.tpe
        }

        name -> (field, fieldType)
      }
    }

    val makeDefs = fieldsWithTypes.map { case (version, fields) =>
      val defName = s"make${version.replace("x", "").replace(".", "")}"

      val params = fields.map { case (field, (fieldInfo, tpe)) =>
        val defaultStr = fieldInfo.default.fold("") { s =>
          (s, fieldInfo.withUndefined, fieldInfo.withNull) match {
            case ("null", true, true)       => "JsonNull"
            case ("undefined", true, true)  => "JsonUndefined"
            case ("null", false, true)      => "None"
            case ("undefined", true, false) => "UndefOrUndefined"
            case _ => sys.error(s"Specified impossible default $s for type $tpeName/$version/$field")
          }
        }
        s"${camelCase(field)}: $tpe$defaultStr"
      }

      val args = fields.map { case (field, (fieldInfo, _)) =>
        val fieldLit = "\"" + field + "\""
        if (fieldInfo.withUndefined) s"$fieldLit :=? ${camelCase(field)}"
        else s"$fieldLit := ${camelCase(field)}"
      }

      val fieldDocs = fields.collect { case (name, (FieldDef(_, _, Some(documentation), _, _, _), _)) =>
        s"@param $name $documentation"
      }.toSeq

      s"${docString("", fieldDocs)} def $defName(${params.mkString(", ")}): $tpeName = makeRawFromFields(${args.mkString(", ")})"
    }

    val allClassFields = fieldsWithTypes.toSeq.flatMap { case (version, fields) =>
      val intVersion = version.replace("x", "").replace(".", "").toInt
      fields.map(field => (intVersion, field._1, field._2))
    }.zipWithIndex
    val highestVersionAll = allClassFields.maxBy(_._1._1)._1._1

    val groupedClassFields = allClassFields
      .groupBy(_._1._2)
      .map { case (k, v) =>
        val (t, idx) = v.maxBy(_._1._1)
        k -> (t._3, t._1, idx)
      }
      .toSeq
      .sortBy(_._2._3)
      .map { case (k, v) =>
        k -> (v._1, v._2)
      }

    val classDefs = groupedClassFields.map { case (k, ((fieldDef, tpe), highestVersion)) =>
      val mods =
        if (highestVersion < highestVersionAll)
          List(
            "@inline",
            s"""@deprecated(message = "Value might be missing", since = "${highestVersionAll.toString}")""",
            s"private[ackcord]"
          )
        else List("@inline")

      val defdef = s"""${mods.mkString(" ")} def ${camelCase(k)}: $tpe = selectDynamic[$tpe]("$k")"""

      (fieldDef.documentation.map(docString(_)).toList :+ defdef).mkString("\n")
    }

    val tpeCode =
      s"""|class $tpeName(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
          |   ${classDefs.mkString("\n\n")}
          |}
          |object ${classTypeDef.name} extends DiscordObjectCompanion[$tpeName] {
          |  def makeRaw(json: Json, cache: Map[String, Any]): $tpeName = new $tpeName(json, cache)
          |
          |  ${makeDefs.mkString("\n\n")}
          |
          |  ${classTypeDef.anonPart.innerTypes.flatMap(codeFromTypeDef).mkString("\n\n")}
          |}""".stripMargin

    (classTypeDef.anonPart.documentation.map(docString(_)).toList :+ tpeCode).mkString("\n")
  }

  def codeFromEnumTypeDef(classTypeDef: TypeDef.EnumTypeDef): String =
    //TODO
    "TODO"

  def knownArgPathElemToCustom(elem: PathElem.ArgPathElem): PathElem.CustomArgPathElem = elem.argOf match {
    case "GuildId" =>
      PathElem.CustomArgPathElem(
        elem.name.getOrElse("guildId"),
        "GuildId",
        majorParameter = true,
        elem.documentation
      )
    case _ =>
      sys.error(s"Unknown path arg element ${elem.argOf}")
  }

  def codeFromRequestDef(requestDef: TypeDef.RequestDef): List[String] = {
    val uncapitalizedName = requestDef.name.charAt(0).toLower.toString + requestDef.name.substring(1)
    val capitalizedName   = uncapitalizedName.capitalize
    val queryClass = requestDef.query.fold("")(q => codeFromClassTypeDef(q.named(capitalizedName + "Query")) + "\n")
    val bodyClass = requestDef.body.fold("") {
      case AnonymousClassTypeDefOrType.TypeRef(_) => ""
      case AnonymousClassTypeDefOrType.AnonType(anon) =>
        codeFromClassTypeDef(anon.named(capitalizedName + "Body")) + "\n"
    }
    val returnClass = requestDef.returnTpe.fold("") {
      case AnonymousClassTypeDefOrType.TypeRef(_) => ""
      case AnonymousClassTypeDefOrType.AnonType(anon) =>
        codeFromClassTypeDef(anon.named(capitalizedName + "Result")) + "\n"
    }

    val allCustomPathElems = requestDef.path.map {
      case argPathElem: PathElem.ArgPathElem => knownArgPathElemToCustom(argPathElem)
      case other                             => other
    }

    val pathParamNames = allCustomPathElems.collect { case PathElem.CustomArgPathElem(name, _, _, _) =>
      name
    }

    val duplicatePathParamNames = pathParamNames.collect {
      case name if pathParamNames.count(_ == name) > 1 => name
    }
    require(
      duplicatePathParamNames.isEmpty,
      s"Found duplicated name for request ${requestDef.name}. Duplicated: ${duplicatePathParamNames.mkString(", ")}"
    )

    val pathDocs = allCustomPathElems.collect { case PathElem.CustomArgPathElem(name, _, _, Some(documentation)) =>
      s"@param $name $documentation"
    }

    val pathParams = allCustomPathElems
      .collect { case PathElem.CustomArgPathElem(name, tpe, _, _) =>
        s"$name: $tpe, "
      }
      .mkString("\n")

    val queryParam =
      if (requestDef.query.isDefined) s"query: ${capitalizedName}Query = ${capitalizedName}Query(), " else ""

    val paramsType = requestDef.body.fold("Unit") {
      case AnonymousClassTypeDefOrType.TypeRef(name)  => name
      case AnonymousClassTypeDefOrType.AnonType(anon) => s"${capitalizedName}Body, "
    }

    val bodyParam = requestDef.body.fold("")(_ => s"body: $paramsType")
    val returnTpe = requestDef.returnTpe.fold("Unit") {
      case AnonymousClassTypeDefOrType.TypeRef(name)  => name
      case AnonymousClassTypeDefOrType.AnonType(anon) => s"${capitalizedName}Result"
    }

    val pathArg = requestDef.path
      .map {
        case arg @ PathElem.ArgPathElem(_, argOf, _) =>
          val custom = knownArgPathElemToCustom(arg)
          s"Parameters.of${argOf.capitalize}(${custom.name})"
        case PathElem.StringPathElem(elem) => s""""$elem""""
        case PathElem.CustomArgPathElem(name, tpe, majorParameter, documentation) =>
          if (majorParameter) s"""Parameters.MajorParameter[$tpe]("$name", $name)"""
          else s"""Parameters.MinorParameter[$tpe]("$name", $name)"""
      }
      .mkString(" / ")

    val queryArg = requestDef.query.filter(_.fields.nonEmpty).fold("") { q =>
      val highestVersion = q.fields.keys.maxBy(_.replace(".", "").replace("x", "").toInt)
      q
        .fields(highestVersion)
        .map { case (k, v) =>
          s""" +? Parameters.query("$k", query.$k)"""
        }
        .mkString
    }

    val pathArgWithQuery = pathArg + queryArg

    //TODO: Audit log reason

    val requestDefDef =
      s"""|${requestDef.documentation.map(docString(_, pathDocs) + "\n")} def $uncapitalizedName(
          |  $pathParams
          |  $queryParam
          |  $bodyParam
          |): Request[$paramsType, $returnTpe] =
          |  Request.restRequest(
          |    route = $pathArgWithQuery,
          |    ${if (bodyParam.nonEmpty) "params = body," else ""}
          |  )
          |""".stripMargin

    List(queryClass, bodyClass, returnClass, requestDefDef).filter(_.nonEmpty)
  }
}

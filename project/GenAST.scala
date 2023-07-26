object GenAST {
  case class ScalaFile(
      packageLoc: String,
      intelliJIgnoredInspections: Seq[String],
      disclaimer: String,
      definitions: Seq[Definition]
  )

  sealed trait Definition

  case class Imports(
      imports: Seq[String]
  ) extends Definition

  case class Grouped(
      definitions: Definition*
  ) extends Definition

  case class Class(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      typeParameters: Seq[TypeParameter] = Nil,
      constructors: Seq[Constructor] = Nil,
      extend: Seq[String] = Nil,
      members: Seq[Definition] = Nil
  ) extends Definition
  case class Module(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      extend: Seq[String] = Nil,
      members: Seq[Definition] = Nil
  ) extends Definition

  case class DefDef(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      typeParameters: Seq[TypeParameter] = Nil,
      parameters: Seq[Seq[Parameter]] = Nil,
      implicitParameters: Seq[Parameter] = Nil,
      returnType: String,
      rhs: String
  ) extends Definition
  case class ValDef(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      returnType: String,
      rhs: String
  ) extends Definition
  case class TypeDef(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      upperBound: Option[String] = None,
      lowerBound: Option[String] = None,
      rhs: Option[String] = None
  ) extends Definition
  case class FreeformDefinition(
      documentation: Option[String] = None,
      content: String
  ) extends Definition

  case class Constructor(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      parameters: Seq[Seq[Parameter]] = Nil,
      implicitParamters: Seq[Parameter] = Nil,
      rhs: Option[String] = None
  )

  case class Parameter(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      tpe: String,
      default: Option[String] = None
  )

  case class TypeParameter(
      docs: Option[String] = None,
      mods: Seq[String] = Nil,
      name: String,
      upperBound: Option[String] = None,
      lowerBound: Option[String] = None
  )

  def printFile(file: ScalaFile): String = {
    s"""|//noinspection ${file.intelliJIgnoredInspections.mkString(", ")}
        |package ${file.packageLoc}
        |
        |${file.disclaimer.linesIterator.mkString("// ", "\n// ", "")}
        |
        |${printDefinitions(file.definitions, indent = 0)}""".stripMargin
  }

  def printDefinitions(definitions: Seq[GenAST.Definition], indent: Int): String =
    definitions.map(printDefinition(_, indent).mkString("\n")).mkString("\n\n")

  def printDefinition(definition: Definition, indent: Int): Seq[String] = definition match {
    case Imports(imports) =>
      imports.map("import " + _)

    case Grouped(definitions @ _*) =>
      definitions.flatMap(printDefinition(_, indent))

    case Class(docs, mods, name, typeParameters, constructors, extend, members) =>
      val paramsMods = constructors.headOption.toSeq.flatMap(_.mods)
      val params = printParameters(
        typeParameters,
        constructors.headOption.map(_.parameters).getOrElse(Nil),
        constructors.headOption.map(_.implicitParamters).getOrElse(Nil),
        indent
      )
      val extendStr = printExtends(extend)

      val memberStr =
        if (members.nonEmpty || constructors.length > 1) {
          val ctorStr = constructors
            .drop(1)
            .map { c =>
              val cparams = printParameters(Nil, c.parameters, c.implicitParamters, indent + 1)
              (makeDocs(c.docs) :+ s"${printMods(c.mods)}def this$cparams = {\n${addIndent(c.rhs.get)}\n}")
                .mkString("\n")
            }
            .mkString("\n\n")

          s"""| {
              |$ctorStr
              |${addIndent(printDefinitions(members, indent + 1))}
              |}""".stripMargin
        } else ""

      val paramModsStr = if(paramsMods.nonEmpty) s" ${printMods(paramsMods)}" else ""

      makeDocs(
        docs,
        types = typeParameters.map(t => t.name -> t.docs),
        params = constructors.headOption.toSeq.flatMap { c =>
          (c.parameters.flatten ++ c.implicitParamters).map(p => p.name -> p.docs)
        }
      ) :+ s"${printMods(mods)}class $name$paramModsStr$params$extendStr$memberStr"

    case Module(docs, mods, name, extend, members) =>
      val extendStr = printExtends(extend)
      val memberStr = if (members.nonEmpty) s" {\n${addIndent(printDefinitions(members, indent + 1))}\n}" else ""

      makeDocs(docs) :+ s"${printMods(mods)}object $name$extendStr$memberStr"

    case DefDef(docs, mods, name, typeParameters, parameters, implicitParameters, returnType, rhs) =>
      val params = printParameters(
        typeParameters,
        parameters,
        implicitParameters,
        indent
      )
      val lhs = s"${printMods(mods)}def $name$params: $returnType = "
      val rhsBody =
        if (rhs.contains("\n") || lhs.linesIterator.toSeq.last.length + indent * 2 > 70)
          "\n" + addIndent(rhs)
        else rhs
      makeDocs(
        docs,
        types = typeParameters.map(t => t.name -> t.docs),
        params = (parameters.flatten ++ implicitParameters).map(p => p.name -> p.docs)
      ) :+ s"$lhs$rhsBody"

    case ValDef(docs, mods, name, returnType, rhs) =>
      val lhs     = s"${printMods(mods)}val $name: $returnType = "
      val rhsBody = if (rhs.contains("\n") || lhs.length + indent * 2 > 70) s"\n  $rhs" else rhs
      makeDocs(docs) :+ s"$lhs$rhsBody"

    case TypeDef(docs, mods, name, upperBound, lowerBound, rhs) =>
      val upper = upperBound.fold("")(t => s" >: $t")
      val lower = lowerBound.fold("")(t => s" <: $t")
      val equal = rhs.fold("")(t => s" = $t")
      makeDocs(docs) :+ s"${printMods(mods)}type $name$lower$upper$equal"

    case FreeformDefinition(docs, content) =>
      makeDocs(docs) ++ addIndent(content).linesIterator.toSeq

  }

  def addIndent(str: String): String = {
    val allLines = str.linesIterator
    allLines.map(str => "  " + str).mkString("\n")
  }

  def fixIndent(str: String, indent: Int): String = {
    val allLines = str.linesIterator
    allLines.map(str => "  " * indent + str).mkString("\n")
  }

  def makeDocs(
      docs: Option[String],
      types: Seq[(String, Option[String])] = Nil,
      params: Seq[(String, Option[String])] = Nil
  ): List[String] = {
    def onlySomeDocs(seq: Seq[(String, Option[String])]): Seq[(String, String)] =
      seq.flatMap(t => t._2.toSeq.map(d => t._1 -> d))

    val typesStr  = onlySomeDocs(types).map(t => s"@tparam ${t._1}\n" + addIndent(t._2))
    val paramsStr = onlySomeDocs(params).map(t => s"@param ${t._1}\n" + addIndent(t._2))

    val docsStr = docs.fold("")(d => s"$d\n")

    val docsParamsBuffer = if (docsStr.nonEmpty && (typesStr.nonEmpty || paramsStr.nonEmpty)) "\n" else ""

    val all = (docsStr + docsParamsBuffer + typesStr.mkString("\n") + paramsStr.mkString("\n")).linesIterator.toSeq

    if (all.isEmpty) Nil
    else {
      val allLines = "/**" +: all.map(s => "  * " + s).toList :+ "  */"

      allLines
    }
  }

  def printMods(mods: Seq[String]): String = if (mods.isEmpty) "" else mods.mkString("", " ", " ")

  def printExtends(extend: Seq[String]): String =
    if (extend.isEmpty) "" else extend.mkString(" extends ", " with ", "")

  def printParameters(
      typeParameters: Seq[TypeParameter],
      parameters: Seq[Seq[Parameter]],
      implicitParameters: Seq[Parameter],
      indent: Int
  ): String = {
    def printTypeParameter(tparam: TypeParameter): String = {
      val upper = tparam.upperBound.fold("")(t => s" >: $t")
      val lower = tparam.lowerBound.fold("")(t => s" <: $t")
      s"${printMods(tparam.mods)}${tparam.name}$upper$lower"
    }

    def printParameter(param: Parameter): String = {
      val defaultStr = param.default.fold("")(d => s" = $d")
      s"${printMods(param.mods)}${param.name}: ${param.tpe}$defaultStr"
    }

    def printParameterBlock(block: Seq[Parameter]): String = {
      val strs   = block.map(printParameter)
      val length = strs.map(_.length).sum

      val shouldDoSeparateLines = length + indent * 2 > 60

      if (shouldDoSeparateLines)
        "(\n" + addIndent(addIndent(strs.map(p => p + ",").mkString("\n"))) + "\n" + ")"
      else strs.mkString("(", ", ", ")")
    }

    val types = if (typeParameters.isEmpty) "" else typeParameters.map(printTypeParameter).mkString("[", ", ", "]")

    val allParameters =
      if (implicitParameters.nonEmpty)
        parameters :+ implicitParameters.zipWithIndex.map(t =>
          if (t._2 == 0) t._1.copy(mods = "implicit" +: t._1.mods) else t._1
        )
      else parameters

    types + allParameters.map(printParameterBlock).mkString
  }
}

/* eslint-disable camelcase */
import OpenAPIParser from '@readme/openapi-parser'
import { OpenAPIV3_1 } from 'openapi-types'

type FileTypeDefs = [string, TypeDef][]
type SchemaReveseMap = Map<OpenAPIV3_1.SchemaObject, string>

const currentVersion = '2.0.x'

const specialHandling = ['SnowflakeType', 'SnowflakeSelectDefaultValueTypes']

const unknownFile = 'unknownSpecDefs.yaml'

function updateSchema<A extends TypeDef | AnonymousClassTypeDef>(
  existing: A,
  name: string,
  update: <B extends TypeDef | AnonymousClassTypeDef>(existing: B) => B,
): A {
  if (existing.specName === name) {
    return update(existing)
  } else if ('defType' in existing) {
    const existingDef: TypeDef = existing
    let typeDefRet: TypeDef
    switch (existingDef.defType) {
      case 'Class':
        typeDefRet = {
          ...existingDef,
          innerTypes: existingDef.innerTypes?.map((inner) => updateSchema(inner, name, update)),
        }
        break
      case 'Enum':
        typeDefRet = {
          ...existingDef,
          innerTypes: existingDef.innerTypes?.map((inner) => updateSchema(inner, name, update)),
        }
        break
      case 'Opaque':
        typeDefRet = {
          ...existingDef,
          innerTypes: existingDef.innerTypes?.map((inner) => updateSchema(inner, name, update)),
        }
        break
      case 'Request':
        typeDefRet = {
          ...existingDef,
          query: existingDef.query !== undefined ? updateSchema(existingDef.query, name, update) : existingDef.query,
          body:
            existingDef.body !== undefined && typeof existingDef.body !== 'string'
              ? updateSchema(existingDef.body, name, update)
              : existingDef.query,
          return:
            existingDef.return !== undefined && typeof existingDef.return !== 'string'
              ? updateSchema(existingDef.return, name, update)
              : existingDef.return,
        }
        break
      case 'Multiple':
        typeDefRet = {
          ...existingDef,
          innerTypes: existingDef.innerTypes.map((inner) => updateSchema(inner, name, update)),
        }
        break
      case 'ObjectOnly':
        typeDefRet = {
          ...existingDef,
          innerTypes: existingDef.innerTypes.map((inner) => updateSchema(inner, name, update)),
        }
        break
      case 'Freeform':
        typeDefRet = existingDef
        break
    }

    return typeDefRet as A
  } else {
    const classExisting: AnonymousClassTypeDef = existing
    const newClass: AnonymousClassTypeDef = {
      ...classExisting,
      innerTypes: classExisting.innerTypes?.map((inner) => updateSchema(inner, name, update)),
    }
    return newClass as A
  }
}

function getScalaType(obj: OpenAPIV3_1.SchemaObject, schemaReverseMap: SchemaReveseMap) {
  let scalaType: ScalaType
  let nullable = false
  let types = [...(Array.isArray(obj.type) ? obj.type : [obj.type])]

  if (types.includes('null')) {
    nullable = true
    types = types.filter((t) => t !== 'null')
  }

  if (types.length > 1) {
    throw new Error("Too many types. Don't know how to handle")
  }

  switch (obj.type) {
    case 'integer':
      switch (obj.format) {
        case 'int8':
          scalaType = 'Byte'
          break
        case 'int16':
          scalaType = 'Short'
          break
        case 'int32':
          scalaType = 'Int'
          break
        case 'int64':
          scalaType = 'Long'
          break
        default:
          scalaType = 'Int'
      }
      break
    case 'number':
      scalaType = 'Double'
      break
    case 'string':
      scalaType = 'String'
      break
    case 'boolean':
      scalaType = 'Boolean'
      break
    case 'array':
      // eslint-disable-next-line no-case-declarations
      const { scalaType: innerTpe, nullable: innerNullable } = getScalaType(
        obj.items as OpenAPIV3_1.SchemaObject,
        schemaReverseMap,
      )
      // eslint-disable-next-line no-case-declarations
      const innerFinalType = innerNullable ? `Option[${innerTpe}]` : innerTpe
      scalaType = `Seq[${innerFinalType}]`

      break
    case 'object':
      // eslint-disable-next-line no-case-declarations
      const tpe = schemaReverseMap.get(obj)
      if (!tpe) {
        throw new Error(`Unknown type for field ${obj}`)
      }
      scalaType = tpe
      break

    default:
      throw new Error(`Unknown type ${obj.type}`)
  }

  return { scalaType, nullable }
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
function augmentSchema(
  existing: FileTypeDefs,
  name: string,
  schema: OpenAPIV3_1.SchemaObject,
  schemaReverseMap: SchemaReveseMap,
): FileTypeDefs {
  if (specialHandling.includes(name)) {
    return existing
  }

  let doneUpdate = false
  const mappedExisting = existing.map<[string, TypeDef]>(([k, v]) => [
    k,
    updateSchema(v, name, (typeDef) => {
      switch (schema.type) {
        case 'object':
          doneUpdate = true
          // eslint-disable-next-line no-case-declarations
          return {
            name,
            ...typeDef,
            specName: name,
            defType: 'Class',
            documentation: schema.description,
            fields: {
              ...(typeDef as ClassTypeDef).fields,
              [currentVersion]: Object.fromEntries(
                Object.entries(schema.properties ?? {}).map(([k, v]) => {
                  const existingFields = (typeDef as ClassTypeDef).fields[currentVersion]
                  const existingField = existingFields && existingFields[k]
                  const existingFieldDef = typeof existingField === 'string' ? null : existingField
                  const obj = v as OpenAPIV3_1.SchemaObject

                  const { scalaType, nullable } = getScalaType(obj, schemaReverseMap)
                  const undef = (obj.required ?? []).includes(k)

                  const fieldDef: FieldDef = {
                    ...existingFieldDef,
                    type: scalaType,
                    withNull: existingFieldDef?.withNull ?? (nullable ? true : undefined),
                    withUndefined: existingFieldDef?.withUndefined ?? (undef ? true : undefined),
                    default: obj.default?.toString() ?? existingFieldDef?.default,
                    documentation: obj.description ?? existingFieldDef?.documentation,
                  }

                  const isSimple = Object.values(fieldDef).filter((v) => v !== null && v !== undefined).length <= 1
                  const ret = isSimple ? scalaType : fieldDef

                  return [k, ret]
                }),
              ),
            },
          }
        case 'integer':
        case 'string':
          doneUpdate = true

          // eslint-disable-next-line no-case-declarations
          const enumValuesObj: { [k: string]: EnumValue } = {}
          for (const untypedObj of schema.oneOf ?? []) {
            const obj = untypedObj as OpenAPIV3_1.SchemaObject
            const existing = (typeDef as EnumTypeDef).values[obj.title ?? obj.const.toString()]

            enumValuesObj[obj.title ?? obj.const.toString()] = {
              value: obj.const.toString(),
              documentation: obj.description ?? (typeof existing !== 'string' ? existing.documentation : undefined),
            }
          }

          return {
            name,
            ...typeDef,
            specName: name,
            defType: 'Enum',
            type: schema.type === 'integer' ? 'Int' : 'String',
            documentation: schema.description ?? (typeDef as EnumTypeDef).documentation,
            values: enumValuesObj,
          }
        default:
          // We error later
          return typeDef
      }
    }),
  ])

  if (doneUpdate) {
    return mappedExisting
  } else {
    const existingUnknown = mappedExisting.find(([k]) => k === unknownFile)
    const unknownDefType = (existingUnknown ? { ...existingUnknown[1] } : undefined) ?? {
      defType: 'Multiple',
      innerTypes: [],
    }
    if (!existingUnknown) {
      mappedExisting.push([unknownFile, unknownDefType])
    }

    if (unknownDefType.defType !== 'Multiple') {
      throw new Error(`Found file ${unknownFile}, but it had type ${unknownDefType.defType}`)
    }

    let typeDefFromSchema: TypeDef | undefined
    switch (schema.type) {
      case 'object':
        typeDefFromSchema = {
          name,
          specName: name,
          defType: 'Class',
          documentation: schema.description,
          fields: {
            [currentVersion]: Object.fromEntries(
              Object.entries(schema.properties ?? {}).map(([k, v]) => {
                const obj = v as OpenAPIV3_1.SchemaObject

                const { scalaType, nullable } = getScalaType(obj, schemaReverseMap)
                const undef = (obj.required ?? []).includes(k)

                const fieldDef: FieldDef = {
                  type: scalaType,
                  withNull: nullable ? true : undefined,
                  withUndefined: undef ? true : undefined,
                  default: obj.default.toString(),
                  documentation: obj.description,
                }

                return [k, fieldDef]
              }),
            ),
          },
        }

        break
      case 'number':
      case 'string':
        typeDefFromSchema = {
          name,
          specName: name,
          defType: 'Enum',
          type: schema.type === 'number' ? 'Int' : 'String',
          documentation: schema.description,
          values: Object.fromEntries(
            (schema.oneOf ?? []).map((obj) => [
              (obj as OpenAPIV3_1.SchemaObject).title,
              {
                value: (obj as OpenAPIV3_1.SchemaObject).const.toString(),
                documentation: obj.description,
              },
            ]),
          ),
        }
        break
      default:
        console.log(`Don't know how to handle schema type ${schema.type}`)
        break
    }

    if (typeDefFromSchema) {
      unknownDefType.innerTypes.push(typeDefFromSchema)
    }

    return mappedExisting
  }
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
function augmentPath(
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  existing: FileTypeDefs,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  path: string,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  pathObj: OpenAPIV3_1.PathItemObject,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  schemaReverseMap: SchemaReveseMap,
): FileTypeDefs {
  throw new Error('Not implemented')
}

export async function defsFromSpec(existingTypeDefs: FileTypeDefs): Promise<FileTypeDefs> {
  const api = (await OpenAPIParser.validate(
    'https://raw.githubusercontent.com/discord/discord-api-spec/main/specs/openapi.json',
  )) as OpenAPIV3_1.Document
  const schemas = api.components?.schemas ?? {}
  const schemaReverseMap = new Map(Object.entries(schemas).map(([k, v]) => [v, k]))

  const typeDefsAugmentedWithSchemas = Object.entries(schemas).reduce(
    (typeDefs, [name, schema]) => augmentSchema(typeDefs, name, schema, schemaReverseMap),
    existingTypeDefs,
  )
  const paths: OpenAPIV3_1.PathsObject = api.paths ?? {}

  return Object.entries(paths).reduce(
    (typeDefs, [path, pathObj]) => augmentPath(typeDefs, path, pathObj as OpenAPIV3_1.PathItemObject, schemaReverseMap),
    typeDefsAugmentedWithSchemas,
  )
}

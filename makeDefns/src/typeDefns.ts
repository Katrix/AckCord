/* eslint-disable @typescript-eslint/no-unused-vars */

interface Versioned<A> {
  '2.0.x': A

  [version: string]: A
}

type ScalaType = string

/** A field within a class */
interface FieldDef {
  /** The type of the field, as seen from the definition of the field */
  type: ScalaType

  /** The string key which is used in JSON for this field */
  jsonName?: string

  /** The default value of this field */
  default?: string

  /** Documentation for the field */
  documentation?: string

  /** Specifies if this field is maybe undefined */
  withUndefined?: boolean

  /** Specifies if this field is maybe null */
  withNull?: boolean

  /** This object extends another object with more fields */
  isExtension?: boolean

  /** Opposite of withUndefined. Makes this field never undefined */
  alwaysPresent?: boolean

  /** If this field should be defined with override */
  override?: boolean

  /** Verification for when the object is constructed by hand */
  verification?: {
    max_length?: number
    min_length?: number
  }
}

/** An anonymous class type definition */
interface AnonymousClassTypeDef {
  specName?: string

  /** A Scaladoc documentation string to add to the definition */
  documentation?: string

  /** Imports to add to this file */
  imports?: string[]

  /** Extra types found in the companion object of this type */
  // eslint-disable-next-line no-use-before-define
  innerTypes?: TypeDef[]

  /** Sets all the body arguments as withUndefined: true. For patch endpoints */
  allUndefined?: boolean

  /** If a partial version of this should be made. A partial version is a copy of this, but with allUndefined: true */
  makePartial?: boolean

  /** Avoid generating the makeRaw function to allow for a custom implementation */
  customMakeRaw?: boolean

  /** The fields of this class */
  fields: Versioned<{ [fieldName: string]: ScalaType | FieldDef }>

  /** The types this class extends */
  extends?: string[]

  /** The types this companion object extends */
  objectExtends?: string[]
}

interface TypeDefBase {
  /** The type of Scala type to generate */
  defType: 'Class' | 'Enum' | 'Opaque' | 'Request' | 'Multiple' | 'ObjectOnly' | 'Freeform'
  specName?: string
}
// eslint-disable-next-line no-use-before-define
type TypeDef = ClassTypeDef | EnumTypeDef | OpaqueTypeDef | RequestDef | MultipleDefs | ObjectOnlyDef | FreeformDefs

/** A class type definition */
interface ClassTypeDef extends TypeDefBase, AnonymousClassTypeDef {
  defType: 'Class'

  /** Name of the type */
  name: string
}

interface EnumValue {
  /** The value of the enum value */
  value: string

  /** Documentation for the enum value */
  documentation?: string
}

/** An enum type definition */
interface EnumTypeDef extends TypeDefBase {
  defType: 'Enum'

  /** Name of the type */
  name: string

  /** A Scaladoc documentation string to add to the definition */
  documentation?: string

  /** Imports to add to this file */
  imports?: string[]

  /** Extra types found in the companion object of this type */
  innerTypes?: TypeDef[]

  /** The underlying type of this enum */
  type: ScalaType

  /** If this enum is a bitfield */
  isBitfield?: boolean

  values: { [name: string]: string | EnumValue }

  /** The types this companion object extends */
  objectExtends?: string[]
}

/** An opaque type definition */
interface OpaqueTypeDef extends TypeDefBase {
  defType: 'Opaque'

  /** Name of the type */
  name: string

  /** A Scaladoc documentation string to add to the definition */
  documentation?: string

  /** Imports to add to this file */
  imports?: string[]

  /** The underlying type of the opaque type */
  underlying: ScalaType

  /** If there should be included an alias outside the companion object of this type. Defaults to true */
  includeAlias: boolean

  /** Extra types found in the companion object of this type */
  innerTypes?: TypeDef[]

  /** The types this companion object extends */
  objectExtends?: string[]
}

type KnownRequestPathArgType =
  | 'GuildId'
  | 'ChannelId'
  | 'ApplicationId'
  | 'CommandId'
  | 'EmojiId'
  | 'MessageId'
  | 'UserId'
  | 'Emoji'
  | 'RoleId'
  | 'GuildScheduledEventId'
  | 'WebhookId'
  | 'webhookToken'
  | 'InteractionId'
  | 'interactionToken'

interface RequestPathArg {
  argOf: KnownRequestPathArgType

  /** Name of the parameter */
  name?: string

  major?: boolean

  /** Docs for this argument */
  documentation?: string
}

interface RequestCustomPathArg {
  customArgType: ScalaType

  /** Name of the parameter */
  name: string

  major?: boolean

  /** Docs for this argument */
  documentation?: string
}

type RequestPathElem = string | RequestPathArg | RequestCustomPathArg

/** A definition for an AckCord request */
interface RequestDef extends TypeDefBase {
  defType: 'Request'

  /** Name of the request */
  name: string

  /** A Scaladoc documentation string to add to the definition */
  documentation?: string

  /** Imports to add to this file */
  imports?: string[]

  path: RequestPathElem[]

  /** The HTTP method for this endpoint */
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

  query?: AnonymousClassTypeDef

  /** If the body type is an array of the body specified */
  arrayOfBody?: boolean

  body?: string | AnonymousClassTypeDef

  /** Content of the parseResponse request parameter */
  parseResponse?: string

  /** If the return type is an array of the return specified */
  arrayOfReturn?: boolean

  return?: string | AnonymousClassTypeDef

  /** If this requests allows to set the audit log reason */
  allowsReason?: boolean

  /** Type parameters to place on the def */
  additionalTypeParams?: ScalaType[]

  additionalParams?: {
    [name: string]: string | { type: string; /** Default value of this parameter */ default?: string }
  }

  complexType?: {
    R1?: ScalaType
    R2?: ScalaType
  }

  /** Scala code for EncodeBody */
  encodeBody?: string
}

/** A definition for multiple entries at the same scope */
interface MultipleDefs extends TypeDefBase {
  defType: 'Multiple'

  /** Imports to add to this file */
  imports?: string[]

  /** Extra types found in the current scope */
  innerTypes: TypeDef[]
}

/** A definition for a companion object only */
interface ObjectOnlyDef extends TypeDefBase {
  defType: 'ObjectOnly'

  /** Name of the object */
  name: string

  /** Imports to add to this file */
  imports?: string[]

  /** Extra types found in the current scope */
  innerTypes: TypeDef[]

  /** The types this object extends */
  objectExtends?: string[]
}

interface FreeformDefs extends TypeDefBase {
  defType: 'Freeform'

  /** The string to insert into the code */
  content: string

  /** A Scaladoc documentation string to add to the definition */
  documentation?: string

  /** Imports to add to this file */
  imports?: string[]
}

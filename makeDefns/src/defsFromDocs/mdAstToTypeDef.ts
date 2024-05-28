/* eslint-disable no-console */
import type { MdAst, MdAstWithContent } from './mdTokensParser'

const CURRENT_VERSION = '2.0.x'

function capitalize(s: string) {
  if (!s) {
    return s
  }

  return s.charAt(0).toUpperCase() + s.substring(1)
}

function camelCase(str: string): string {
  return str.charAt(0).toLowerCase() + str.replaceAll(' ', '').substring(1)
}

const discordToScalaTypes: { [k: string]: string } = {
  integer: 'Int',
  snowflake: 'RawSnowflake',
  boolean: 'Boolean',
  'ISO8601 timestamp': 'OffsetDateTime',
}

const snowflakeTypes: { [k: string]: string } = {
  guild_id: 'GuildId',
  user_id: 'UserId',
  channel_id: 'ChannelId',
}

const handledTypes = [
  ...Object.values(discordToScalaTypes),
  ...Object.values(snowflakeTypes),
  'ImageHash',
  'Permissions',
]

const knownParameters: { [k: string]: RequestPathArg | RequestCustomPathArg } = {
  'guild.id#DOCS_RESOURCES_GUILD/guild-object': { argOf: 'GuildId' },
  'channel.id#DOCS_RESOURCES_CHANNEL/channel-object': { argOf: 'ChannelId' },
  'application.id': { argOf: 'ApplicationId' },
  'command.id': { argOf: 'CommandId' },
  'message.id#DOCS_RESOURCES_CHANNEL/message-object': { argOf: 'MessageId' },
  'user.id#DOCS_RESOURCES_USER/user-object': { argOf: 'UserId' },
  emoji: { argOf: 'Emoji' },
  'overwrite.id#DOCS_RESOURCES_CHANNEL/overwrite-object': {
    name: 'overwriteId',
    customArgType: 'Snowflake[UserOrRoleId]',
  },
}

const requestHeaderRegex = / % (?:GET|POST|PATCH|DELETE|PUT)/gm

function trustExistingType(str: string, existing: string): boolean {
  return str === 'snowflake' || !handledTypes.includes(existing)
}

function toScalaType(str: string, ctx?: { name: string; documentation?: string }): string {
  function pascalCase(s: string) {
    return s.split(' ').map(capitalize).join('')
  }

  const arrayMatch = /array of ((?:(?!s? objects).)+)/gm.exec(str)
  if (arrayMatch) {
    const inner = arrayMatch[1]
    return `Seq[${toScalaType(inner.endsWith('s') ? inner.substring(0, inner.length - 1) : inner)}]`
  }

  const anObjectMatch = /^an? (.+) object$/gm.exec(str)
  if (anObjectMatch) {
    str = anObjectMatch[1]
  }

  let ret: string | null = null
  if (str === 'snowflake' && ctx && snowflakeTypes[ctx.name]) {
    ret = snowflakeTypes[ctx.name]
  }

  if (str === 'string' && ctx?.documentation?.includes('hash')) {
    ret = 'ImageHash'
  }

  if (str === 'permissions') {
    ret = 'Permissions'
  }

  if (discordToScalaTypes[str]) {
    ret = discordToScalaTypes[str]
  }

  if (ret) {
    // return trustExistingType(ret, undefined) ? undefined : ret
    return ret
  }

  return pascalCase(str)
}

function isNullOrUndefined<V>(v: V | null | undefined): v is undefined | null {
  return typeof v === 'undefined' || v === null
}

export type NullableFromOpt<A, Opt extends boolean> = Opt extends false ? A : A | null

export class MdAstConsumer<Opt extends boolean> {
  readonly #file: string
  readonly #typedefs: { [k: string]: TypeDef }
  readonly #ast: MdAst[]
  readonly #mode: 'consume' | 'peek'
  readonly #opt: Opt
  readonly #lineSpansNotRead: { from: number; to: number }[]

  constructor(
    file: string,
    typedefs: { [k: string]: TypeDef },
    ast: MdAst[],
    mode: 'consume' | 'peek',
    opt: Opt,
    lineSpansNotRead: { from: number; to: number }[],
    copy: boolean = true,
  ) {
    this.#file = file
    this.#typedefs = typedefs
    if (copy) {
      this.#ast = structuredClone(ast)
    } else {
      this.#ast = ast
    }
    this.#mode = mode
    this.#opt = opt
    this.#lineSpansNotRead = lineSpansNotRead
  }

  static newFromAst(file: string, typedefs: { [k: string]: TypeDef }, ast: MdAst[]): MdAstConsumer<false> {
    return new MdAstConsumer<false>(file, typedefs, ast, 'consume', false, [])
  }

  withMode(mode: 'consume' | 'peek') {
    return new MdAstConsumer<Opt>(this.#file, this.#typedefs, this.#ast, mode, this.#opt, this.#lineSpansNotRead, false)
  }

  withOpt<Opt2 extends boolean>(opt: Opt2) {
    return new MdAstConsumer<Opt2>(
      this.#file,
      this.#typedefs,
      this.#ast,
      this.#mode,
      opt,
      this.#lineSpansNotRead,
      false,
    )
  }

  optional(): MdAstConsumer<true> {
    return this.withOpt(true)
  }

  peeking(): MdAstConsumer<Opt> {
    return this.withMode('peek')
  }

  nonEmpty(ctx: MdAst[] = this.#ast) {
    return ctx.length > 0
  }

  markSpansNotRead(...spans: { from: number; to: number }[]) {
    this.#lineSpansNotRead.push(...spans)
  }

  lineSpansNotRead() {
    return [...this.#lineSpansNotRead]
  }

  assertEmpty(ctx: MdAst[] = this.#ast) {
    if (this.nonEmpty(ctx)) {
      throw new Error('Expected all AST to be consumed', { cause: this.optional().peeking().use([], ctx) })
    }
  }

  assertNextHeading(ctx: MdAst[] = this.#ast) {
    const next = this.optional().peeking().use([], ctx)
    if (next && next.type !== 'heading') {
      throw new Error('Expected next element to be a heading', { cause: next })
    }
  }

  use<Type extends MdAst['type']>(
    type: Type | Type[],
    ctx: MdAst[] = this.#ast,
  ): NullableFromOpt<MdAst & { type: Type }, Opt> {
    let head: MdAst | undefined
    if (this.#mode === 'consume') {
      head = ctx.shift()
    } else {
      head = ctx[0]
    }

    const typeArr: string[] = typeof type === 'string' ? [type] : type

    if (head === undefined && !this.#opt) {
      throw new Error(`No nodes left to consume ${type}`)
    } else if (typeArr.length > 0 && head && typeArr.every((t) => head.type !== t)) {
      if (this.#opt) {
        if (this.#mode === 'consume') {
          ctx.unshift(head)
        }

        return null as NullableFromOpt<MdAst & { type: Type }, Opt>
      } else {
        throw new Error(`Expected ${type}, but found ${head.type}`, { cause: head.map })
      }
    }

    return (head ?? null) as NullableFromOpt<MdAst & { type: Type }, Opt>
  }

  ignore<Type extends MdAst['type']>(type: Type | Type[], ctx: MdAst[] = this.#ast) {
    const ast = this.use(type, ctx)
    if (ast) {
      this.markSpansNotRead(...(ast.map ?? []))
    }
  }

  ignoreHeadingAndContent(headerText: string | null, ctx: MdAst[] = this.#ast) {
    if (headerText) {
      const headingContent = this.peeking().useText('heading', ctx)
      if (headingContent === null) {
        return
      }

      if (headerText !== headingContent) {
        throw new Error(`Tried to ignore heading and content. Expected ${headerText}, but found ${headingContent}`, {
          cause: this.peeking().use([], ctx),
        })
      }
    }
    this.ignore('heading', ctx)

    while (
      (() => {
        const ast = this.optional().peeking().use<MdAst['type']>([], ctx)
        return ast && ast.type !== 'heading'
      })()
    ) {
      this.ignore([], ctx)
    }
  }

  useAndPrint<Type extends MdAst['type']>(type: Type | Type[], ctx: MdAst[] = this.#ast) {
    const consumed = this.use<Type>(type, ctx)
    console.dir(consumed, { depth: 9 })
  }

  useHeading(
    levels: number | number[],
    ctx: MdAst[] = this.#ast,
  ): NullableFromOpt<MdAstWithContent & { type: 'heading' }, Opt> {
    const heading = this.use('heading', ctx)
    if (heading === null) {
      return null as NullableFromOpt<MdAstWithContent & { type: 'heading' }, Opt>
    }

    const levelsArr = Array.isArray(levels) ? levels : [levels]

    if (heading.markup.match(/^#+$/g)) {
      if (levelsArr.includes(heading.markup.length)) {
        return heading
      } else if (this.#opt) {
        if (this.#mode === 'consume') {
          ctx.unshift(heading)
        }
        return null as NullableFromOpt<MdAstWithContent & { type: 'heading' }, Opt>
      } else {
        throw new Error(`Expected heading with level ${levels}, but found ${heading.markup.length}`, { cause: heading })
      }
    } else {
      throw new Error(`Unknown heading markup ${heading.markup}`, { cause: heading })
    }
  }

  useText<Type extends MdAst['type']>(type: Type | Type[], ctx: MdAst[] = this.#ast): NullableFromOpt<string, Opt> {
    const consumed = this.use<Type>(type, ctx)
    if (consumed === null) {
      return null as NullableFromOpt<string, Opt>
    }

    if ('content' in consumed) {
      if (typeof consumed.content === 'string') {
        return consumed.content as string
      } else if (consumed.content) {
        return consumed.content.map((c) => this.useText([], [c])).join('')
      } else {
        return ''
      }
    } else if (consumed.type === 'softbreak') {
      return '\n'
    } else if (consumed.type === 'hardbreak') {
      return '\n\n'
    } else {
      throw new Error(`${JSON.stringify(type)} does not have content`, { cause: consumed })
    }
  }

  useMdText<Type extends MdAst['type']>(type: Type | Type[], ctx: MdAst[] = this.#ast): NullableFromOpt<string, Opt> {
    const consumed = this.use<Type>(type, ctx)
    if (consumed === null) {
      return null as NullableFromOpt<string, Opt>
    }

    const raw: MdAst = consumed

    switch (raw.type) {
      case 'text':
        return raw.content.replaceAll('*', '\\*').replaceAll('_', '\\_')
      case 'blockquote':
        return raw.content
          .map((c) => this.useMdText([], [c]))
          .filter((v) => v !== null)
          .map((v) => '> ' + (v ?? ''))
          .join('\n')
      case 'code_inline':
        return `\`${raw.content}\``
      case 'softbreak':
        return '\n'
      case 'hardbreak':
        return '\n\n'
      case 'paragraph':
      case 'link':
        return raw.content.map((c) => this.useMdText([], [c]) ?? '').join('')
      case 'bullet_list':
        return raw.content
          .map((c) => this.useMdText([], [c]))
          .filter((v) => v != null)
          .map((v) => v ?? '')
          .join('\n')
      case 'list_item':
        return raw.content.map((c) => `${raw.markup} ${this.useMdText([], [c])}` ?? '').join('')

      case 'em':
      case 'strong':
        return `${raw.markup}${raw.content.map((c) => this.useMdText([], [c]) ?? '').join('')}${raw.markup}`
      default:
        throw new Error(`${raw.type} is not supported by useMdText`, { cause: raw })
    }
  }

  useHeadingText(level: number, ctx: MdAst[] = this.#ast): NullableFromOpt<string, Opt> {
    const heading = this.useHeading(level, ctx)
    return (heading?.content?.map((c) => this.useText([], [c])).join('') ?? null) as NullableFromOpt<string, Opt>
  }

  useThTd(inHeader: boolean, ctx: MdAst[]) {
    const thTd = this.use(inHeader ? 'th' : 'td', ctx)
    return thTd && thTd.content.map((c) => this.useText([], [c])).join('')
  }

  useTr(inHeader: boolean, ctx: MdAst[]) {
    const tr = this.use('tr', ctx)
    return tr && tr.content.map((c) => this.useThTd(inHeader, [c]))
  }

  useThead(ctx: MdAst[]) {
    const thead = this.use('thead', ctx)
    return thead && this.useTr(true, thead.content)
  }

  useTbody(ctx: MdAst[]) {
    const tbody = this.use('tbody', ctx)
    return tbody && tbody.content.map((c) => this.useTr(false, [c]))
  }

  useTable(ctx: MdAst[] = this.#ast) {
    const table = this.use('table', ctx)
    const head = table && this.useThead(table.content)
    const body = table && this.useTbody(table.content)

    return { head, body }
  }

  #combineDocs(doc1: string | null, doc2: string | null): string | null {
    if (doc1 && doc2) {
      return doc1 + '\n\n' + doc2
    } else if (doc1) {
      return doc1
    } else {
      return doc2
    }
  }

  useDocs(ctx: MdAst[] = this.#ast) {
    let documentation = null
    while (this.peeking().optional().use(['paragraph', 'blockquote', 'bullet_list'], ctx)) {
      documentation = this.#combineDocs(
        documentation,
        this.optional().useMdText(['paragraph', 'blockquote', 'bullet_list'], ctx),
      )
    }

    return documentation
  }

  useAnonClass(
    documentation: string | null,
    allUndefined: boolean,
    ctx: MdAst[] = this.#ast,
  ): NullableFromOpt<AnonymousClassTypeDef, Opt> {
    documentation = this.#combineDocs(documentation, this.useDocs(ctx))

    const { head, body } = this.useTable(ctx)
    if (head === null || body === null) {
      return null as NullableFromOpt<AnonymousClassTypeDef, Opt>
    }
    const headLower = head.map((s) => s?.toLowerCase() ?? null)

    function m1ToNull(v: number) {
      return v === -1 ? null : v
    }

    const fieldIdx = m1ToNull(headLower.indexOf('field')) ?? headLower.indexOf('param')
    const typeIdx = headLower.indexOf('type')
    const descriptionIdx = headLower.indexOf('description')

    if (headLower.length > 3) {
      const knownColumnIndices = [
        fieldIdx,
        typeIdx,
        descriptionIdx,
        headLower.indexOf('default'),
        headLower.indexOf('permission'),
        headLower.indexOf('channel type'),
      ]
      const unknownHead = headLower.filter((v, i) => !knownColumnIndices.includes(i)).join(', ')

      if (unknownHead.length > 0) {
        console.warn(`Unknown type table head cells ${unknownHead}`, headLower)
      }
    }

    if (isNullOrUndefined(fieldIdx) || isNullOrUndefined(typeIdx)) {
      throw new Error(
        `Field name and type name for type fields is required, but was not found. Got ${headLower.join(', ')}`,
      )
    }

    if (body.length <= 0) {
      throw new Error('No body rows for type table')
    }

    const fields = Object.fromEntries(
      body.map((row) => {
        const name = row[fieldIdx] as string
        const type = row[typeIdx] as string
        const docs = row[descriptionIdx] ? capitalize(row[descriptionIdx] as string) : undefined

        const allowsUndefined = name.endsWith('?')
        const withNull = type.startsWith('?')

        return [
          allowsUndefined ? name.substring(0, name.length - 1) : name,
          {
            type: toScalaType(withNull ? type.substring(1) : type, { name, documentation: docs }),
            withUndefined: allUndefined || !allowsUndefined ? undefined : allowsUndefined,
            withNull: withNull ? true : undefined,
            documentation: docs,
          },
        ]
      }),
    )

    while (this.peeking().optional().useText(['paragraph', 'blockquote', 'bullet_list'])) {
      documentation = this.#combineDocs(
        documentation,
        this.optional().useMdText(['paragraph', 'blockquote', 'bullet_list']),
      )
    }

    this.optional().ignore('fence')

    this.assertNextHeading(ctx)

    return {
      documentation: documentation ?? undefined,
      allUndefined: allUndefined || undefined,
      fields: {
        [CURRENT_VERSION]: fields,
      },
    }
  }

  useClass(
    documentation: string | null,
    allUndefined: boolean,
    ctx: MdAst[] = this.#ast,
  ): NullableFromOpt<ClassTypeDef, Opt> {
    const text = this.useHeadingText(6, ctx)
    if (text === null) {
      return null as NullableFromOpt<ClassTypeDef, Opt>
    } else if (!text.endsWith(' Structure')) {
      throw new Error(`Tried to use structure, but found ${text}`)
    }

    const name = toScalaType(text.substring(0, text.length - ' Structure'.length))

    const rest = this.useAnonClass(documentation, allUndefined, ctx)
    return {
      name,
      defType: 'Class',
      ...rest,
    }
  }

  useEnum(documentation: string | null, ctx: MdAst[] = this.#ast): NullableFromOpt<EnumTypeDef, Opt> {
    const text = this.useHeadingText(6, ctx)
    if (text === null) {
      return null as NullableFromOpt<EnumTypeDef, Opt>
    }

    documentation = this.#combineDocs(documentation, this.useDocs(ctx))

    const name = toScalaType(text)
    const { head, body } = this.useTable(ctx)
    if (head === null || body === null) {
      return null as NullableFromOpt<EnumTypeDef, Opt>
    }
    const headLower = head.map((s) => s?.toLowerCase() ?? null)

    function m1ToNull(v: number) {
      return v === -1 ? null : v
    }

    const nameIdx = m1ToNull(headLower.indexOf('name')) ?? 0
    const valueIdx =
      m1ToNull(headLower.indexOf('value')) ??
      m1ToNull(headLower.indexOf('integer')) ??
      (headLower.indexOf('description') === 1 || headLower.length < 3 ? 0 : 1)
    const descriptionIdx = m1ToNull(headLower.indexOf('description')) ?? Math.min(2, headLower.length - 1)

    if (headLower.length > 3) {
      const knownColumnIndices = [nameIdx, valueIdx, descriptionIdx]
      const unknownHead = headLower.filter((v, i) => !knownColumnIndices.includes(i)).join(', ')
      console.warn(`Unknown enum table head cells ${unknownHead}`, headLower)
    }

    if (body.length <= 0) {
      throw new Error('No body rows for enum table')
    }

    const values: { [k: string]: EnumValue } = Object.fromEntries(
      body.map((row) => {
        const name = row[nameIdx]
        const value = row[valueIdx]

        return [
          name,
          {
            value,
            documentation: capitalize(row[descriptionIdx] as string),
          } as EnumValue,
        ]
      }),
    )

    const firstValue = body[0][valueIdx] as string
    const isBitfield = firstValue.includes('<<')

    documentation = this.#combineDocs(documentation, this.useDocs(ctx))

    this.assertNextHeading(ctx)

    return {
      name,
      defType: 'Enum',
      type: !Number.isNaN(parseInt(firstValue)) || isBitfield ? 'Int' : 'String',
      isBitfield: isBitfield ? true : undefined,
      documentation: documentation ?? undefined,
      values,
    } as EnumTypeDef
  }

  useData(extraDocumentation: string | null, ctx: MdAst[] = this.#ast): TypeDef {
    this.useHeading([2, 3], ctx)
    const res: (ClassTypeDef | EnumTypeDef)[] = []
    let namePrefix: MdAstWithContent | undefined

    while (ctx.length > 0) {
      const documentation = this.#combineDocs(extraDocumentation, this.useDocs(ctx))
      extraDocumentation = null

      if (namePrefix && this.optional().peeking().useHeadingText(6, ctx)) {
        const heading = this.useHeading(6, ctx)
        if (heading !== null) {
          ctx.unshift({ ...heading, content: [...namePrefix.content, ...heading.content] })
        }
      }

      const h6Content = this.optional().peeking().useHeadingText(6, ctx)
      if (h6Content !== null) {
        if (h6Content.endsWith(' Structure')) {
          // Receiving and responding special case
          if (h6Content === 'Interaction Callback Data Structure' && this.useHeading(6, ctx.slice(1))) {
            const ast = this.useHeading(6, ctx)
            if (ast) {
              namePrefix = ast
            }
          } else {
            const classDef = this.useClass(documentation, false, ctx)
            if (classDef) {
              res.push(classDef)
            }
          }
        } else if (
          h6Content.startsWith('Example') ||
          ['Example', 'Examples', 'Allowed Mentions Reference'].some((v) => h6Content.endsWith(v))
        ) {
          this.ignoreHeadingAndContent(null, ctx)
        } else {
          const consumed = []
          let hasTable = false
          consumed.push(this.use('heading') as MdAst)
          while (ctx.length > 0) {
            const ast = this.use<MdAst['type']>([])
            if (ast?.type === 'table') {
              hasTable = true
              consumed.push(ast)
              break
            }
            if (ast?.type === 'heading' && ast.markup === '######') {
              consumed.push(ast)
              break
            }

            if (ast) {
              consumed.push(ast)
            }
          }
          ctx.unshift(...consumed)

          if (hasTable) {
            const enumDef = this.useEnum(documentation, ctx)
            if (enumDef) {
              res.push(enumDef)
            }
          } else {
            extraDocumentation = this.useText('heading') + '\n\n' + this.useDocs(ctx)
          }
        }
      } else if (this.optional().peeking().use('heading', ctx) !== null) {
        break
      } else {
        throw new Error(`Unknown state`, { cause: this.peeking().use([]) })
      }
    }

    if (extraDocumentation) {
      const last = res[res.length - 1]
      last.documentation = this.#combineDocs(last.documentation ?? null, extraDocumentation) ?? undefined
    }

    const first = res.shift() as TypeDef
    return { ...first, innerTypes: res.length ? res : undefined } as TypeDef
  }

  useRequest(ctx: MdAst[] = this.#ast): NullableFromOpt<RequestDef | MultipleDefs, Opt> {
    const nameHeader = this.useHeadingText(2, ctx)
    if (!nameHeader) {
      return null as NullableFromOpt<RequestDef, Opt>
    }
    const nameMatch = /^(.+) % (GET|POST|PATCH|DELETE|PUT) (\/.*)$/gm.exec(nameHeader)
    if (!nameMatch) {
      throw new Error(`Unknown header for request`, { cause: nameHeader })
    }
    const name = camelCase(nameMatch[1])
    const method = nameMatch[2]

    function* makePath(rawPathStr: string): Generator<RequestPathElem> {
      const wholePath = rawPathStr
      if (rawPathStr.startsWith('/')) {
        rawPathStr = rawPathStr.substring(1)
      }

      function splitPath(rawPath: string): [RequestPathElem, string] {
        if (rawPath.startsWith('{')) {
          const before = rawPath.substring(1, rawPath.indexOf('}'))
          const after = rawPath.substring(rawPath.indexOf('}') + 1)

          if (knownParameters[before]) {
            return [knownParameters[before], after]
          } else {
            return [{ customArgType: 'unknown', name: before }, after]
          }
        } else {
          const before = rawPath.substring(0, rawPath.indexOf('/'))
          const after = rawPath.substring(rawPath.indexOf('/') + 1)

          return [before, after]
        }
      }

      while (rawPathStr.length > 0 && rawPathStr.includes('/')) {
        const [pathElem, rest] = splitPath(rawPathStr)
        if (pathElem !== '') {
          yield pathElem
        }
        rawPathStr = rest
      }

      if (rawPathStr.length > 0) {
        const [pathElem, leftover] = splitPath(rawPathStr)
        if (pathElem === '') {
          yield leftover
        } else {
          yield pathElem
          if (leftover.length > 0) {
            throw new Error(`Unconsumed part of path ${leftover} from ${rawPathStr}, ${wholePath}`)
          }
        }
      }
    }

    const path = Array.from(makePath(nameMatch[3]))

    let documentation = null
    let query: AnonymousClassTypeDef | undefined
    let allowsReason: boolean | undefined
    const body: { [k: string]: AnonymousClassTypeDef } = {}
    let returns: AnonymousClassTypeDef | undefined
    const extraTypes: TypeDef[] = []
    let encounteredHr = false

    while (!this.optional().peeking().useHeading(2, ctx) && this.nonEmpty(ctx) && !encounteredHr) {
      const docs = this.optional().useMdText(['paragraph', 'bullet_list'], ctx)
      if (docs) {
        documentation = this.#combineDocs(documentation, docs)
      } else {
        const blockquote = this.optional().use('blockquote', ctx)
        if (blockquote) {
          if (blockquote.content.length !== 1 || blockquote.content[0].type !== 'paragraph') {
            throw new Error('Unexpected blockquote content', { cause: blockquote })
          }

          const content = [...blockquote.content[0].content]
          const tpe = content.shift()
          const softbreak = content.shift()
          const str = content.shift()
          if (tpe?.type !== 'text' || softbreak?.type !== 'softbreak' || str?.type !== 'text') {
            throw new Error('Unexpected block quote', { cause: blockquote })
          }

          if (str.content === 'This endpoint supports the X-Audit-Log-Reason header.') {
            allowsReason = true
          } else if (str.content !== 'All parameters to this endpoint are optional' || method !== 'PATCH') {
            this.markSpansNotRead(...blockquote.map)
          }
        } else {
          const headingAst = this.optional().peeking().useHeading(6, ctx)
          const heading = this.optional().useHeadingText(6, ctx)
          switch (heading) {
            case 'Query String Params':
            case 'Query Params':
              query = this.useAnonClass(null, true, ctx) ?? undefined
              break

            case 'Bulk Application Command JSON Params':
            case 'JSON Params':
              // eslint-disable-next-line no-case-declarations
              const cls1 = this.useAnonClass(null, method === 'PATCH', ctx) ?? undefined
              if (cls1) {
                body.default = cls1
              }
              break

            case 'Bulk Ban Response':
            case 'Response Body':
              returns = this.useAnonClass(null, false, ctx) ?? undefined
              break

            case 'JSON/Form Params':
              // TODO: Handle specially
              // eslint-disable-next-line no-case-declarations
              const cls2 = this.useAnonClass(null, method === 'PATCH', ctx) ?? undefined
              if (cls2) {
                body.default = cls2
              }
              break

            // Special case for Guild.md
            // Special case for Channel.md
            case 'Caveats':
            case 'Limitations':
              documentation = this.#combineDocs(documentation, heading)
              documentation = this.#combineDocs(documentation, this.useDocs(ctx))
              break

            // Special case for Guild.md
            case 'Widget Style Options':
              if (headingAst) {
                ctx.unshift(headingAst)
              }
              // eslint-disable-next-line no-case-declarations
              const enum1 = this.useEnum(null, ctx)
              if (enum1) {
                extraTypes.push(enum1)
              }
              break

            case null:
              if (this.optional().peeking().use('fence', ctx)) {
                this.use('fence')
              } else if (this.optional().peeking().use('hr', ctx)) {
                this.use('hr', ctx)
                encounteredHr = true
              } else {
                throw new Error('Unknown ast element', { cause: this.peeking().use([], ctx) })
              }
              break

            default:
              if (heading.startsWith('Example')) {
                if (headingAst) {
                  ctx.unshift(headingAst)
                }
                this.ignoreHeadingAndContent(null, ctx)
              } else if (heading.endsWith(' Object')) {
                const cls = this.useAnonClass(null, false, ctx)
                if (cls) {
                  extraTypes.push({
                    name: toScalaType(heading.substring(0, heading.length - ' Object'.length)),
                    defType: 'Class',
                    ...cls,
                  })
                }
              } else if (heading.startsWith('JSON Params (') && heading.endsWith(')')) {
                const cls = this.useAnonClass(null, method === 'PATCH', ctx) ?? undefined
                if (cls) {
                  body[heading.substring('JSON Params ('.length, heading.length - 1)] = cls
                }
              } else {
                throw new Error(`Unknown heading ${heading}`, { cause: headingAst })
              }
          }
        }
      }
    }

    this.assertNextHeading(ctx)

    const bodyEntries = Object.entries(body)

    const requestDef: RequestDef = {
      name,
      defType: 'Request',
      documentation: documentation ?? undefined,
      method: method as 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH',
      path,
      query,
      allowsReason,
      body:
        bodyEntries.length === 0
          ? undefined
          : bodyEntries.length === 1 && bodyEntries[0][0] === 'default'
            ? bodyEntries[0][1]
            : body,
      return: returns ?? 'TODO: Figure out',
    }

    if (extraTypes.length > 0) {
      return {
        defType: 'Multiple',
        innerTypes: [requestDef, ...extraTypes],
      }
    } else {
      return requestDef
    }
  }

  useStandardResourceFile(useHeading: boolean, useData: boolean, useRequests: boolean, ctx: MdAst[] = this.#ast) {
    if (useHeading) {
      this.useHeading([1, 2], ctx)
    }

    const data: TypeDef[] = []
    if (useData) {
      const description = this.useDocs(ctx)

      data.push(this.useData(description, ctx))
      while (this.nonEmpty(ctx) && this.peeking().optional().useHeading(3, ctx)) {
        data.push(this.useData(null, ctx))
      }
    }

    const requests: (RequestDef | MultipleDefs)[] = []
    if (useRequests) {
      while (this.nonEmpty(ctx) && this.peeking().optional().useHeadingText(2, ctx)?.match(requestHeaderRegex)) {
        const req = this.useRequest(ctx)
        if (req) {
          requests.push(req)
        }
      }
    }

    // console.log(JSON.stringify(datas, null, 2))
    // console.log(JSON.stringify(requests, null, 2))

    return { data, requests }
  }
}

/* eslint-disable no-console */
import markdownIt, { Token } from 'markdown-it'

// eslint-disable-next-line no-use-before-define
export type MdAst = MdAstText | MdAstWithContent | MdAstUnknownContent

export interface MdAstBase {
  map: { from: number; to: number }[]
}

export interface MdAstText extends MdAstBase {
  type: 'text' | 'code_inline' | 'fence'
  content: string
  info?: string
}

export interface MdAstWithContent extends MdAstBase {
  type:
    | 'heading'
    | 'table'
    | 'thead'
    | 'tbody'
    | 'tr'
    | 'th'
    | 'td'
    | 'blockquote'
    | 'paragraph'
    | 'bullet_list'
    | 'list_item'
    | 'link'
    | 'em'
    | 'strong'
  content: MdAst[]
  markup: string
  attrs?: { [k: string]: string }
}

export interface MdAstUnknownContent extends MdAstBase {
  type: 'softbreak' | 'hardbreak' | 'hr' | 'code_block'
}

function parseMdTokens(tokens: Token[]): MdAst[] {
  const blocks: [Partial<MdAstWithContent>, MdAst[]][] = []
  const root: MdAst[] = []

  // noinspection JSMismatchedCollectionQueryUpdate
  let currentBlock: MdAst[] = root

  function ensureMiscPropertiesMissing(
    head: Token,
    allow: ('attrs' | 'children' | 'info' | 'meta' | 'content')[] = [],
  ) {
    if (head.attrs && !allow.includes('attrs')) {
      throw new Error(`Unexpected attrs ${head.attrs} for ${head.type}`)
    }
    if (head.children && !allow.includes('children')) {
      throw new Error(`Unexpected children for ${head.type}`)
    }
    if (head.info && !allow.includes('info')) {
      throw new Error(`Unexpected info ${head.info} for ${head.type}`)
    }
    if (head.meta && !allow.includes('meta')) {
      throw new Error(`Unexpected meta (${head.meta}) for ${head.type}`)
    }
    if (head.content && !allow.includes('content')) {
      throw new Error(`Unexpected string content (${head.content}) for ${head.type}`)
    }
  }

  function open(type: string, token: Token) {
    ensureMiscPropertiesMissing(token, ['attrs'])
    const attrs = token.attrs
    const newBlock: MdAst[] = []
    const base: Partial<MdAstWithContent> = {
      type: type as never,
      markup: token.markup,
      map: token.map ? [{ from: token.map[0], to: token.map[1] }] : [],
    }
    if (attrs) {
      base.attrs = Object.fromEntries(attrs)
    }
    blocks.push([base, newBlock])
    currentBlock = newBlock
    if (token.nesting !== 1) {
      console.log(`Nesting ${type}, but nesting (${token.nesting}) was not 1`)
    }
  }

  function close(type: string, token: Token) {
    ensureMiscPropertiesMissing(token)
    const [popped, blockAst] = blocks.pop() as [Partial<MdAstWithContent>, MdAst[]]
    if (popped.type !== type) {
      throw new Error(`Illegal state, popped ${popped.type}, but expected ${type}`)
    }

    if (blocks.length > 0) {
      currentBlock = blocks[blocks.length - 1][1]
    } else {
      currentBlock = root
    }

    currentBlock.push({
      ...popped,
      content: blockAst,
      map: [...(popped.map ?? []), ...(token.map ? [{ from: token.map[0], to: token.map[1] }] : [])],
    } as MdAst)

    if (token.nesting !== -1) {
      console.log(`Unnesting ${type}, but nesting (${token.nesting}) was not -1`)
    }
  }

  while (tokens.length > 0) {
    const head = tokens.shift() as Token

    if (head.type.endsWith('_open')) {
      open(head.type.substring(0, head.type.length - '_open'.length), head)
    } else if (head.type.endsWith('_close')) {
      close(head.type.substring(0, head.type.length - '_close'.length), head)
    } else {
      switch (head.type) {
        case 'text':
        case 'code_inline':
        case 'fence':
          if (head.nesting !== 0) {
            console.log(`Not nesting, but nesting (${head.nesting}) was not 0`)
          }
          if (head.type === 'fence' && head.info === 'py') {
            break
          }

          ensureMiscPropertiesMissing(head, ['content', 'info'])
          currentBlock.push({
            type: head.type,
            content: head.content ?? '',
            map: head.map ? [{ from: head.map[0], to: head.map[1] }] : [],
            info: head.info
          })
          break

        case 'softbreak':
        case 'hardbreak':
        case 'hr':
        case 'code_block':
          if (head.nesting !== 0) {
            console.log(`Not nesting, but nesting (${head.nesting}) was not 0`)
          }
          ensureMiscPropertiesMissing(head)
          currentBlock.push({ type: head.type, map: head.map ? [{ from: head.map[0], to: head.map[1] }] : [] })
          break

        case 'image':
          break

        case 'inline':
          if (head.children) {
            currentBlock.push(...parseMdTokens(head.children))
          }
          break
        default:
          throw new Error(`Unknown token ${head.type}`)
      }
    }
  }

  if (blocks.length > 0) {
    throw new Error(`Unconsumed blocks ${blocks}`)
  }

  return root
}

export function parseMdAstFromString(markdown: string) {
  const md = markdownIt({})
  const tokens = md.parse(markdown, {})
  return parseMdTokens(tokens)
}

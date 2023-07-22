const {Document} = await import('https://unpkg.com/yaml@2.3.1/browser/dist/index.js')

const CURRENT_VERSION = '2.0.x'
const START_AT_ID = 'integration-create'
const END_AT_ID = null

const unknownClassnames = {}

function makeError(message, elem) {
    return new Error(message, {cause: elem})
}

function hasClass(elem, classStartsWith) {
    if (unknownClassnames[classStartsWith]) {
        return elem.classList.contains(unknownClassnames[classStartsWith])
    } else {
        unknownClassnames[classStartsWith] ||= Array.from(elem.classList.values()).find(cl => cl.startsWith(classStartsWith))
        return Boolean(unknownClassnames[classStartsWith])
    }
}

function checkTagAndClass(elem, tag, classStartsWith, elemLocation) {
    if (elem.tagName !== tag || (classStartsWith && !hasClass(elem, classStartsWith))) {
        throw makeError(`Expected ${elemLocation} to be ${tag}${classStartsWith ? ' with class ' + classStartsWith : ''}. Got tag ${elem.tagName} with classes ${elem.classList}`, elem)
    }

    return elem
}

function isNullOrUndefined(v) {
    return typeof v === 'undefined' || v === null
}

function capitalize(s) {
    if (!s) {
        return s
    }

    return s.charAt(0).toUpperCase() + s.substring(1)
}

const discordToScalaTypes = {
    'integer': 'Int',
    'string': 'String',
    'snowflake': 'RawSnowflake',
    'boolean': 'Boolean',
    'ISO8601 timestamp': 'OffsetDateTime'
}

function toScalaType(str) {
    function pascalCase(s) {
        return s.split(' ').map(capitalize).join('')
    }

    const arrRegex = /array of ((?:(?!s? objects).)+)/gm
    const match = arrRegex.exec(str)
    if (match) {
        return `Seq[${toScalaType(match[1])}]`
    }

    if (discordToScalaTypes[str]) {
        return discordToScalaTypes[str]
    }

    return pascalCase(str)
}

function parseType(tableElem, allUndefined) {
    const headCells = tableElem.tHead?.rows?.item(0)?.cells
    if (!headCells) {
        throw makeError('No head for type table', tableElem)
    }

    const headCellsArr = Array.from(headCells).map(el => el.innerText.toLowerCase())
    const fieldIdx = headCellsArr.indexOf('field')
    const typeIdx = headCellsArr.indexOf('type')
    const descriptionIdx = headCellsArr.indexOf('description')

    if (headCellsArr.length > 3) {
        const knownColumnIndices = [fieldIdx, typeIdx, descriptionIdx]
        const unknownHead = headCellsArr.filter((v, i) => !knownColumnIndices.includes(i)).join(', ')
        console.warn({message: `Unknown type table head cells ${unknownHead}`, elem: tableElem})
    }

    if (isNullOrUndefined(fieldIdx) || isNullOrUndefined(typeIdx)) {
        throw makeError(`Field name and type name for type fields is required, but was not found. Got ${headCellsArr.join(', ')}`, tableElem)
    }

    const rows = tableElem.tBodies?.item(0)?.rows
    if (!rows) {
        throw makeError('No body rows for type table', tableElem)
    }

    const fields = Object.fromEntries(Array.from(rows).map((row) => {
        const name = row.cells.item(fieldIdx).innerText
        const type = row.cells.item(typeIdx).innerText

        const allowsUndefined = name.endsWith('?')
        const withNull = type.startsWith('?')

        return [allowsUndefined ? name.substring(0, name.length - 1) : name, {
            type: toScalaType(withNull ? type.substring(1) : type),
            withUndefined: allUndefined || !allowsUndefined ? undefined : allowsUndefined,
            withNull: withNull ? true : undefined,
            documentation: capitalize(row.cells.item(descriptionIdx).innerText),
        }]
    }))

    const doc = new Document(fields)
    doc.contents.items.forEach((pair, idx) => {
        pair.key.spaceBefore = idx !== 0
    })

    return {
        fields: {
            [CURRENT_VERSION]: doc.contents
        }
    }
}

function camelCase(str) {
    return str.charAt(0).toLowerCase() + str.replaceAll(' ', '').substring(1)
}

// Gateway event specific code

function makeGatewayEventDef(h4Elem) {
    const doc = new Document()
    checkTagAndClass(h4Elem, 'H4', 'h4', 'h4')

    const name = h4Elem.innerText.replaceAll(' ', '')

    let documentation
    let fields

    let elem = h4Elem.nextElementSibling
    let hasSeenMeaningfulElement = false
    while (elem && elem.tagName !== 'H4' && !hasClass(elem, 'h4')) {
        if (!hasSeenMeaningfulElement && elem.tagName === 'P' && hasClass(elem, 'paragraph')) {
            if (!documentation) {
                documentation = elem.innerText
            } else {
                documentation += '\n\n' + elem.innerText
            }
            elem = elem.nextElementSibling
        } else {
            const inner = elem.innerText
            if (inner.endsWith(' Event Fields')) {
                fields = parseType(elem.nextElementSibling, false)
                elem = elem.nextElementSibling.nextElementSibling
            } else {
                console.warn({message: `Unknown text of element: ${elem.innerText}`, elem})
                elem = elem.nextElementSibling
            }
        }
    }

    let documentationNode
    if (documentation) {
        documentationNode = doc.createNode(documentation)
        documentationNode.type = documentation.includes('\n') ? 'BLOCK_LITERAL' : 'BLOCK_FOLDED'
    }

    return new Document({
        name,
        defType: 'Class',
        documentation: documentationNode,
        ...fields ?? {
            fields: {
                [CURRENT_VERSION]: {
                    todo: {
                        type: 'TODO',
                        isExtension: true
                    }
                }
            }
        }
    })
}

function makeAllGatewayEventDefs() {
    //Prime and fetch the h4 class name
    if (!Array.from(document.getElementsByTagName('h4')).some(d => hasClass(d, 'h4'))) {
        throw new Error('Failed to get h4 class')
    }

    const allH4s = Array.from(document.getElementsByClassName(unknownClassnames['h4']))
    let allH4sSlice = allH4s
    if (START_AT_ID || END_AT_ID) {
        const start = document.getElementById(START_AT_ID)
        const end = document.getElementById(END_AT_ID)
        const m1ToUndef = (v) => v === -1 ? undefined : v

        allH4sSlice = allH4s.slice(m1ToUndef(allH4s.indexOf(start)), m1ToUndef(allH4s.indexOf(end)));
    }

    const doc = new Document(allH4sSlice.map((h4) => {
        try {
            return makeGatewayEventDef(h4)
        } catch (e) {
            console.error({message: e, elem: h4})
        }
    }).filter(r => Boolean(r)))
    doc.contents.items.forEach((node, idx) => {
        node.spaceBefore = idx !== 0
    })

    return doc
}

console.log(makeAllGatewayEventDefs().toString())

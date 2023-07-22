const {Document} = await import('https://unpkg.com/yaml@2.3.1/browser/dist/index.js')

const CURRENT_VERSION = '2.0.x'
const START_AT_ID = null
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
    if(match) {
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

// Data specific code

function parseEnum(tableElem, name, documentation) {
    const headCells = tableElem.tHead?.rows?.item(0)?.cells
    if (!headCells) {
        throw makeError('No head for enum table', tableElem)
    }

    function m1ToNull(v) {
        return v === -1 ? null : v
    }

    const headCellsArr = Array.from(headCells).map(el => el.innerText.toLowerCase())
    const nameIdx = 0
    const valueIdx = m1ToNull(headCellsArr.indexOf('value')) ?? m1ToNull(headCellsArr.indexOf('integer')) ?? (headCellsArr.indexOf('description') === 1 || headCellsArr.length < 3 ? 0 : 1)
    const descriptionIdx = m1ToNull(headCellsArr.indexOf('description')) ?? Math.min(2, headCellsArr.length - 1)

    if (headCellsArr.length > 3) {
        const knownColumnIndices = [nameIdx, valueIdx, descriptionIdx]
        const unknownHead = headCellsArr.filter((v, i) => !knownColumnIndices.includes(i)).join(', ')
        console.warn({message: `Unknown enum table head cells ${unknownHead}`, elem: tableElem})
    }

    const rows = tableElem.tBodies?.item(0)?.rows
    if (!rows) {
        throw makeError('No body rows for enum table', tableElem)
    }

    const values = Object.fromEntries(Array.from(rows).map((row) => {
        const name = row.cells.item(nameIdx).innerText
        const value = row.cells.item(valueIdx).innerText

        const doc = new Document()
        const valueNode = doc.createNode(value)
        valueNode.type = 'QUOTE_DOUBLE'

        return [name, {
            value: valueNode,
            documentation: capitalize(row.cells.item(descriptionIdx).innerText),
        }]
    }))

    const doc = new Document(values)
    doc.contents.items.forEach((pair, idx) => {
        pair.key.spaceBefore = idx !== 0
    })

    const firstValue = doc.contents.items[0].value.items[0].value.value

    const isBitfield = firstValue.includes("<<")

    return {
        name,
        defType: 'Enum',
        type: !Number.isNaN(parseInt(firstValue)) || isBitfield ? 'Int' : 'String',
        isBitfield: isBitfield ? true : undefined,
        documentation,
        values: doc.contents,
    }
}

function makeDataDef(h3Elem) {
    checkTagAndClass(h3Elem, 'H3', null, 'h3')

    let name
    let documentation
    let hasSeenMeaningfulElement = false
    let nextType

    let resultDef

    let elem = h3Elem.nextElementSibling
    let firstDef = true

    while (elem && elem.tagName !== 'H3' && !hasClass(elem, 'container')) {
        if (elem.tagName === 'H6') {
            if (!firstDef) {
                name = undefined
                documentation = undefined
                hasSeenMeaningfulElement = false
                nextType = undefined
            }
            firstDef = false

            const inner = elem.innerText
            if (inner.endsWith(' Structure')) {
                name = toScalaType(inner.substring(0, inner.length - ' Structure'.length))
                nextType = 'Class'
            } else if (inner.startsWith('Example')) {
                elem = elem.nextElementSibling
            } else {
                name = toScalaType(inner)
                nextType = 'Enum'
            }
        } else if (!hasSeenMeaningfulElement && elem.tagName === 'P' && hasClass(elem, 'paragraph')) {
            if (!documentation) {
                documentation = elem.innerText
            } else {
                documentation += '\n\n' + elem.innerText
            }
        } else if (elem.tagName === 'TABLE') {
            hasSeenMeaningfulElement = true

            let res
            if (nextType === 'Class') {
                res = {name, defType: 'Class', documentation, ...parseType(elem, false)}
            } else if (nextType === 'Enum') {
                res = parseEnum(elem, name, documentation)
            } else {
                throw makeError('Got type or enum before name of the definition', elem)
            }

            if (resultDef) {
                if (!resultDef.innerTypes) {
                    resultDef.innerTypes = []
                }

                resultDef.innerTypes.push(res)
            } else {
                resultDef = res
            }
        } else {
            console.warn({message: 'Unknown element', elem})
        }

        elem = elem.nextElementSibling
    }

    if (resultDef?.innerTypes) {
        const doc = new Document(resultDef)
        doc.contents.items.forEach((pair) => {
            if (pair.key.value === 'innerTypes') {
                pair.key.spaceBefore = true
                pair.value.items.forEach((node, idx) => {
                    node.spaceBefore = idx !== 0
                })
            }
        })

        return doc.contents
    } else {
        return resultDef
    }
}

function makeAllDataDefs() {
    //Prime and fetch the h3 class name
    if (!Array.from(document.getElementsByTagName('h3')).some(d => hasClass(d, 'h3'))) {
        throw new Error('Failed to get h3 class')
    }

    const allH3s = Array.from(document.getElementsByClassName(unknownClassnames['h3']))
    let allH3sSlice = allH3s

    if (START_AT_ID || END_AT_ID) {
        const start = document.getElementById(START_AT_ID)
        const end = document.getElementById(END_AT_ID)
        const m1ToUndef = (v) => v === -1 ? undefined : v

        allH3sSlice = allH3sSlice.slice(m1ToUndef(allH3s.indexOf(start)), m1ToUndef(allH3s.indexOf(end)));
    }

    const doc = new Document(allH3sSlice.map((h3) => {
        try {
            return makeDataDef(h3)
        } catch (e) {
            console.error({message: e, elem: h3})
        }
    }).filter(r => Boolean(r)))
    doc.contents.items.forEach((node, idx) => {
        node.spaceBefore = idx !== 0
    })

    return doc
}

console.log(makeAllDataDefs().toString())

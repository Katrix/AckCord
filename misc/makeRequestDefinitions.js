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

// Request specific code

const knownParameters = {
    'guild.id': {argOf: 'GuildId'},
    'channel.id': {argOf: 'ChannelId'},
    'application.id': {argOf: 'ApplicationId'},
    'command.id': {argOf: 'CommandId'},
    'message.id': {argOf: 'MessageId'},
    'user.id': {argOf: 'UserId'},
    'emoji': {argOf: 'Emoji'},
    'overwrite.id': {name: 'overwriteId', customArgType: 'Snowflake[UserOrRoleId]'}
}

const newParameters = []

function* makePathFromElems(childNodeIt) {
    for (const child of childNodeIt) {
        const text = child.innerText
        if (text === '') {
            continue
        }

        if (text.startsWith('{')) {
            const innerText = text.substring(1, text.length - 1)
            if (knownParameters[innerText]) {
                yield knownParameters[innerText]
            } else {
                //TODO: Maybe make a dialog later?
                const tpe = prompt(`What type should a path argument of ${innerText} be given? If a JSON string is given, it will be used`)

                try {
                    const parsed = JSON.parse(tpe)
                    knownParameters[innerText] = parsed
                    yield parsed
                } catch {
                    const res = {argOf: tpe}
                    newParameters.push(tpe)
                    knownParameters[innerText] = res
                    yield res
                }
            }
        } else {
            yield* text.split('/').filter(s => s.length > 0)
        }
    }
}

function makeRequestDef(containerElement) {
    const doc = new Document()
    checkTagAndClass(containerElement, 'DIV', 'container', 'container')

    const nameHeader = checkTagAndClass(containerElement.firstChild, 'H2', 'title', 'first child of container')
    const name = camelCase(nameHeader.innerText)

    const methodSpan = checkTagAndClass(nameHeader.nextElementSibling, 'SPAN', 'verb', 'second child of container')
    const method = methodSpan.innerText

    const pathSpan = checkTagAndClass(methodSpan.nextElementSibling, 'SPAN', 'url', 'third child of container')
    const path = doc.createNode(Array.from(makePathFromElems(pathSpan.childNodes.values())), null, {flow: true})

    let documentation
    let query
    let allowsReason
    let body
    let returns

    const optionalBehaviors = {
        'This endpoint supports the X-Audit-Log-Reason header.'() {
            allowsReason = true
        },
        'Query String Params'(tableElem) {
            query = parseType(tableElem, true)
            query = {allUndefined: true, ...query}
            return tableElem.nextElementSibling
        },
        'JSON Params'(tableElem) {
            body = parseType(tableElem, method === 'PATCH')
            if (method === 'PATCH') {
                body = {allUndefined: true, ...body}
            }
            return tableElem.nextElementSibling
        },
        'Response Body'(tableElem) {
            returns = parseType(tableElem, false)
            return tableElem.nextElementSibling
        }
    }

    function parseOptionalParams(elem) {
        let hasSeenMeaningfulElement = false
        while (elem && !hasClass(elem, 'container')) {
            if (!hasSeenMeaningfulElement && elem.tagName === 'P' && hasClass(elem, 'paragraph')) {
                if (!documentation) {
                    documentation = elem.innerText
                } else {
                    documentation += '\n\n' + elem.innerText
                }
                elem = elem.nextElementSibling
            } else {
                const behavior = optionalBehaviors[elem.innerText]
                if (behavior) {
                    hasSeenMeaningfulElement = true
                    elem = behavior(elem.nextElementSibling) || elem.nextElementSibling
                } else {
                    console.warn({message: `Unknown text of element: ${elem.innerText}`, elem})
                    elem = elem.nextElementSibling
                }
            }
        }
    }

    parseOptionalParams(containerElement.nextElementSibling)

    let documentationNode
    if (documentation) {
        documentationNode = doc.createNode(documentation)
        documentationNode.type = documentation.includes('\n') ? 'BLOCK_LITERAL' : 'BLOCK_FOLDED'
    }

    return new Document({
        name,
        defType: 'Request',
        documentation: documentationNode,
        method,
        path,
        query,
        allowsReason,
        body,
        'return': returns ?? 'TODO: Figure out'
    })
}

function makeAllRequestDefs() {
    //Prime and fetch the container class name
    if (!Array.from(document.getElementsByTagName('div')).some(d => hasClass(d, 'container'))) {
        throw new Error('Failed to get container class')
    }

    const allContainers = Array.from(document.getElementsByClassName(unknownClassnames['container']))
    let allContainersSlice = allContainers
    if (START_AT_ID || END_AT_ID) {
        const start = document.getElementById(START_AT_ID)?.parentElement
        const end = document.getElementById(END_AT_ID)?.parentElement
        const m1ToUndef = (v) => v === -1 ? undefined : v

        allContainersSlice = allContainers.slice(m1ToUndef(allContainers.indexOf(start)), m1ToUndef(allContainers.indexOf(end)));
    }

    const doc = new Document(allContainersSlice.map((container) => {
        try {
            return makeRequestDef(container)
        } catch (e) {
            console.error({message: e, elem: container})
        }
    }).filter(r => Boolean(r)))
    doc.contents.items.forEach((node, idx) => {
        node.spaceBefore = idx !== 0
    })

    return doc
}

function postProcessDocument(yamlString) {
    return yamlString.replaceAll(/path:(\s+\[[^\]]+])/gm, (substr, values) => 'path: ' + values.split('\n').map(s => s.trim()).join(' '))
}

console.log(postProcessDocument(makeAllRequestDefs().toString()))

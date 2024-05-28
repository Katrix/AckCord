/* eslint-disable no-case-declarations */
import fs from 'fs/promises'
import path from 'node:path'
import { simpleGit } from 'simple-git'
import { getExistingDefs } from '../getExistingDefs'
import { parseMdAstFromString, type MdAst } from './mdTokensParser'
import { MdAstConsumer } from './mdAstToTypeDef'
import { githubDiffLink } from './util'
import yaml from 'yaml'

const ignoredFiles = [
  '.github/',
  'ci/',
  'docs/game_sdk/',
  'docs/dispatch/',
  'docs/rich_presence',
  'docs/ja/',
  'docs/nl/',
  'docs/pl/',
  'docs/tr/',
  'images/',
  'package-lock.json',
  'package.json',
  'docs/developer_tools/Embedded_App_SDK.md',
]

const automaticFiles = [
  'docs/resources/',
  'docs/topics/OAuth2.md',
  'docs/topics/Permissions.md',
  'docs/topics/Gateway_Events.md',
  'docs/interactions/Application_Commands.md',
  'docs/interactions/Message_Components.md',
  'docs/interactions/Receiving_and_Responding.md',
  'docs/monetization/Entitlements.md',
  'docs/monetization/SKUs.md',
]

type FileTypeDefs = { [file: string]: TypeDef }

function handlePermissions(c: MdAstConsumer<false>): { [k: string]: TypeDef } {
  // console.log(file, 'permissions')
  return {}
}

function handleOAuth2(c: MdAstConsumer<false>): { [k: string]: TypeDef } {
  // console.log(file, 'oauth2')
  return {}
}

function handleGatewayEvents(c: MdAstConsumer<false>): { [k: string]: TypeDef } {
  // console.log(file, 'gateway')
  return {}
}

async function defsFromDocsFile(
  file: string,
): Promise<[{ [k: string]: string[] }, MdAst[], (c: MdAstConsumer<false>) => { [k: string]: TypeDef }]> {
  const contents = await fs.readFile(`./docsRepo/${file}`, { encoding: 'utf-8' })
  const ast = parseMdAstFromString(contents)
  await fs.mkdir(`./debug/${path.dirname(file)}`, { recursive: true })
  await fs.writeFile(`./debug/${file}.json`, JSON.stringify(ast))

  function dataTypeDefNames(filename: string) {
    filename = filename.replace(/_/g, '')
    const dataBase = '../data/src/main/resources/generated/ackcord/data/'
    const requestBase = '../requests/src/main/resources/generated/ackcord/requests/'
    const uncapitalizedFilename = filename.charAt(0).toLowerCase() + filename.slice(1)
    return {
      data: [`${dataBase}${filename}.yaml`, `${dataBase}${uncapitalizedFilename}.yaml`],
      requests: [`${requestBase}${filename}Requests.yaml`],
    }
  }

  const standardResourceHandler = (c: MdAstConsumer<false>) => {
    c.useStandardResourceFile(true, true, true)
    c.assertEmpty()
    return {}
  }

  const handlers: {
    [file: string]: [{ [k: string]: string[] }, (c: MdAstConsumer<false>) => { [k: string]: TypeDef }]
  } = {
    'docs/topics/Permissions.md': [{}, handlePermissions], // TODO
    'docs/topics/OAuth2.md': [
      { default: ['../data/src/main/resources/generated/ackcord/data/OAuth2Scope.yaml'] },
      handleOAuth2,
    ],
    'docs/topics/Gateway_Events.md': [
      {
        events: ['../gateway/src/main/resources/generated/ackcord/gateway/data/GatewayDispatchEvent.yaml'],
        eventTypes: ['../gateway/src/main/resources/generated/ackcord/gateway/data/GatewayDispatchType.yaml'],
        intents: ['../gateway/src/main/resources/generated/ackcord/gateway/data/GatewayIntents.yaml'],
      },
      handleGatewayEvents,
    ],
    'docs/interactions/Application_Commands.md': [
      {
        data: ['../interactions/src/main/resources/generated/ackcord/interactions/data/ApplicationCommand.yaml'],
        requests: [
          '../interactions/src/main/resources/generated/ackcord/interactions/data/ApplicationCommandRequests.yaml',
        ],
      },
      (c) => {
        c.useStandardResourceFile(true, true, false)
        c.ignoreHeadingAndContent('Authorizing Your Application')
        c.ignoreHeadingAndContent('Registering a Command')
        c.ignoreHeadingAndContent('Making a Global Command')
        c.ignoreHeadingAndContent('Making a Guild Command')
        c.ignoreHeadingAndContent('Updating and Deleting a Command')
        c.ignoreHeadingAndContent('Contexts')
        c.ignoreHeadingAndContent('Installation Context')
        c.ignoreHeadingAndContent('Interaction Contexts')
        c.ignoreHeadingAndContent('Permissions')
        c.ignoreHeadingAndContent('Syncing and Unsyncing Permissions')
        c.useData(null)
        c.ignoreHeadingAndContent('Slash Commands')
        c.ignoreHeadingAndContent('Example Slash Command')
        c.ignoreHeadingAndContent('Example Interaction')
        c.ignoreHeadingAndContent('Subcommands and Subcommand Groups')
        c.ignoreHeadingAndContent('Example Walkthrough')
        c.ignoreHeadingAndContent('User Commands')
        c.ignoreHeadingAndContent('Example User Command')
        c.ignoreHeadingAndContent('Example Interaction')
        c.ignoreHeadingAndContent('Message Commands')
        c.ignoreHeadingAndContent('Example Message Command')
        c.ignoreHeadingAndContent('Example Interaction')
        c.ignoreHeadingAndContent('Autocomplete')
        c.ignoreHeadingAndContent('Localization')
        c.ignoreHeadingAndContent('Retrieving localized commands')
        c.ignoreHeadingAndContent('Age-Restricted Commands')
        c.ignoreHeadingAndContent('Using Age-Restricted Commands')
        c.ignoreHeadingAndContent('Endpoints')
        c.useStandardResourceFile(false, false, true)
        c.assertEmpty()
        return {}
      },
    ],
    'docs/interactions/Message_Components.md': [{ default: [] }, standardResourceHandler], // TODO
    'docs/interactions/Receiving_and_Responding.md': [
      {
        data: ['../interactions/src/main/resources/generated/ackcord/interactions/data/Interaction.yaml'],
        requests: ['../interactions/src/main/resources/generated/ackcord/interactions/data/InteractionRequests.yaml'],
      },
      (c) => {
        c.useStandardResourceFile(true, true, false)
        c.ignoreHeadingAndContent('Receiving an Interaction')
        c.ignoreHeadingAndContent('Interaction Metadata')
        c.ignoreHeadingAndContent('Responding to an Interaction')
        c.useStandardResourceFile(false, true, false)
        c.ignoreHeadingAndContent('Followup Messages')
        c.ignoreHeadingAndContent('Endpoints')
        c.useStandardResourceFile(false, false, true)
        c.assertEmpty()
        return {}
      },
    ],
    'docs/monetization/Entitlements.md': [
      dataTypeDefNames('Entitlement'),
      (c) => {
        c.useStandardResourceFile(true, true, true)
        c.ignoreHeadingAndContent('Gateway Events')
        c.ignoreHeadingAndContent('New Entitlement')
        c.ignoreHeadingAndContent('Updated Entitlement')
        c.ignoreHeadingAndContent('Deleted Entitlement')
        c.ignoreHeadingAndContent('Using Entitlements in Interactions')
        c.ignoreHeadingAndContent('PREMIUM_REQUIRED Interaction Response')
        c.ignoreHeadingAndContent('Checking Entitlements in Interactions')
        c.assertEmpty()
        return {}
      },
    ],
    'docs/monetization/SKUs.md': [
      dataTypeDefNames('SKU'),
      (c) => {
        c.useStandardResourceFile(true, true, false)
        c.ignoreHeadingAndContent('Customizing Your SKUs')
        c.ignoreHeadingAndContent('Adding Benefits to Your SKU')
        c.ignoreHeadingAndContent('Using a Unicode Emoji')
        c.ignoreHeadingAndContent('Using a Custom Emoji')
        c.ignoreHeadingAndContent('Publishing Your SKUs')
        c.useStandardResourceFile(false, false, true)
        c.assertEmpty()
        return {}
      },
    ],
    'docs/resources/Application.md': [
      dataTypeDefNames('Application'),
      (c) => {
        c.useStandardResourceFile(true, true, false)
        c.ignoreHeadingAndContent('Installation Context')
        c.ignoreHeadingAndContent('Server Context')
        c.ignoreHeadingAndContent('User Context')
        c.ignoreHeadingAndContent('Setting Supported Installation Contexts')
        c.useStandardResourceFile(false, false, true)
        c.assertEmpty()
        return {}
      },
    ],
    'docs/resources/Channel.md': [
      dataTypeDefNames('Channel'),
      (c) => {
        c.useStandardResourceFile(true, true, false)
        c.ignoreHeadingAndContent('Message Types')
        c.ignoreHeadingAndContent('Crosspost messages')
        c.ignoreHeadingAndContent('Channel Follow Add messages')
        c.ignoreHeadingAndContent('Pin messages')
        c.ignoreHeadingAndContent('Replies')
        c.ignoreHeadingAndContent('Thread Created messages')
        c.ignoreHeadingAndContent('Thread starter messages')
        c.ignoreHeadingAndContent('Voice Messages')
        c.useStandardResourceFile(false, true, true)
        c.assertEmpty()
        return {}
      },
    ],
    'docs/resources/Guild_Scheduled_Event.md': [
      dataTypeDefNames('Guild_Scheduled_Event'),
      (c) => {
        c.useStandardResourceFile(true, true, true)
        c.ignoreHeadingAndContent('Guild Scheduled Event Status Update Automation')
        c.ignoreHeadingAndContent(
          'An active scheduled event for a stage channel where all users have left the stage channel will automatically end a few minutes after the last user leaves the channel',
        )
        c.ignoreHeadingAndContent(
          'An active scheduled event for a voice channel where all users have left the voice channel will automatically end a few minutes after the last user leaves the channel',
        )
        c.ignoreHeadingAndContent('An external event will automatically begin at its scheduled start time')
        c.ignoreHeadingAndContent('An external event will automatically end at its scheduled end time')
        c.ignoreHeadingAndContent(
          'Any scheduled event which has not begun after its scheduled start time will be automatically cancelled after a few hours',
        )
        c.ignoreHeadingAndContent('Guild Scheduled Event Permissions Requirements')
        c.ignoreHeadingAndContent('Permissions for events with entity_type: STAGE_INSTANCE')
        c.ignoreHeadingAndContent('Get Permissions')
        c.ignoreHeadingAndContent('Create Permissions')
        c.ignoreHeadingAndContent('Modify/Delete Permissions')
        c.ignoreHeadingAndContent('Permissions for events with entity_type: VOICE')
        c.ignoreHeadingAndContent('Get Permissions')
        c.ignoreHeadingAndContent('Create permissions')
        c.ignoreHeadingAndContent('Modify/Delete Permissions')
        c.ignoreHeadingAndContent('Permissions for events with entity_type: EXTERNAL')
        c.ignoreHeadingAndContent('Get Permissions')
        c.ignoreHeadingAndContent('Create permissions')
        c.ignoreHeadingAndContent('Modify/Delete Permissions')
        c.assertEmpty()
        return {}
      },
    ],
    'docs/resources/Pool.md': [
      dataTypeDefNames('Pool'),
      (c) => {
        c.useStandardResourceFile(true, true, false)

        c.assertEmpty()
        return {}
      },
    ],
  }

  const t = handlers[file]
  if (t) {
    return [t[0], ast, t[1]]
  } else if (file.startsWith('docs/resources/')) {
    return [dataTypeDefNames(path.parse(file).name), ast, standardResourceHandler]
  } else {
    throw new Error(`Unknown handler for ${file}`)
  }
}

export async function defsFromDocs(existingTypeDefs: FileTypeDefs): Promise<FileTypeDefs> {
  const lastCommit = await fs.readFile('./LAST_DOCS_COMMIT', { encoding: 'utf-8' })

  try {
    await fs.access('./docsRepo')
    await simpleGit('./docsRepo').pull()
  } catch (e) {
    await simpleGit().clone('https://github.com/discord/discord-api-docs.git', './docsRepo')
  }
  const logResponse = await simpleGit('./docsRepo').diff(['--name-only', lastCommit, 'origin/main'])
  const filesChanged = logResponse
    .split('\n')
    .filter((s) => Boolean(s))
    .filter((f) => !ignoredFiles.some((ignored) => f.startsWith(ignored)))

  const autoFiles = []
  const manualFiles = []

  for (const file of filesChanged) {
    if (automaticFiles.some((auto) => file.startsWith(auto))) {
      autoFiles.push(file)
    } else {
      manualFiles.push(file)
    }
  }

  /*
  console.log(automaticFiles)
  for (const manualFile of manualFiles) {
    console.log(manualFile, await githubDiffLink(lastCommit, 'main', manualFile))
  }
  */

  const currentTypeDefs = { ...existingTypeDefs }
  for (const file of autoFiles) {
    console.log(file)
    const [requestedFiles, ast, handler] = await defsFromDocsFile(file)
    const typeDefsWithFile = await Promise.all(
      Object.entries(requestedFiles).map<Promise<[string, [string, TypeDef]]>>(async ([k, files]) => {
        for (const file of files) {
          try {
            await fs.access(file)
          } catch (e) {
            continue
          }

          const content = await fs.readFile(file, { encoding: 'utf-8' })
          return [k, [file, yaml.parse(content)]]
        }

        throw new Error(`No files found for ${k} among ${files}`)
      }),
    )
    const typeDefs = Object.fromEntries(typeDefsWithFile.map(([k, [, v]]) => [k, v]))
    const consumer = MdAstConsumer.newFromAst(file, typeDefs, ast)

    handler(consumer)
  }

  return currentTypeDefs
}

async function run() {
  const existingDefs = await getExistingDefs()
  const res = await defsFromDocs(existingDefs)
}

run()

import fs from 'fs/promises'
import yaml from 'yaml'

const defnFolders = [
  '../data/src/main/resources/generated/ackcord/data',
  '../gateway/src/main/resources/generated/ackcord/gateway/data',
  '../interactions/src/main/resources/generated/ackcord/interactions/data',
  '../requests/src/main/resources/generated/ackcord/requests',
]

export async function getExistingDefs(): Promise<{ [file: string]: TypeDef }> {
  const allFiles = await Promise.all(
    defnFolders.map(async (folder) => {
      const files = await fs.readdir(folder)
      return files.map((file) => `${folder}/${file}`)
    }),
  )

  const allDefsPromise = allFiles.flat(1).map<Promise<[string, TypeDef]>>(async (file) => {
    const content = await fs.readFile(file, { encoding: 'utf-8' })
    const parsed = yaml.parse(content) as TypeDef
    return [file, parsed]
  })

  const res = await Promise.all(allDefsPromise)
  return Object.fromEntries(res)
}

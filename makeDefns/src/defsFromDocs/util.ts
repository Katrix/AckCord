// https://stackoverflow.com/a/48161723
async function sha256(message: string) {
  // encode as UTF-8
  const msgBuffer = new TextEncoder().encode(message)

  // hash the message
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer)

  // convert ArrayBuffer to Array
  const hashArray = Array.from(new Uint8Array(hashBuffer))

  // convert bytes to hex string
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}

export async function githubDiffLink(fromCommit: string, toCommit: string, file: string): Promise<string> {
  return `https://github.com/discord/discord-api-docs/compare/${fromCommit}...${toCommit}#diff-${await sha256(file)}`
}
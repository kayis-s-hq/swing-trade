import type { StartJobRunRequest } from '../api/types'

export interface RunOptions {
  variantIds: string[]
  symbolsText: string
  skipLlm: boolean
  dryRun: boolean
}

export function emptyRunOptions(): RunOptions {
  return { variantIds: [], symbolsText: '', skipLlm: false, dryRun: false }
}

export function parseSymbols(text: string): string[] {
  const seen = new Set<string>()
  for (const part of text.split(/[\s,]+/)) {
    const symbol = part.trim().toUpperCase()
    if (symbol) seen.add(symbol)
  }
  return [...seen]
}

/** Returns undefined when everything is at its default so no body is sent. */
export function toStartRequest(options: RunOptions): StartJobRunRequest | undefined {
  const symbols = parseSymbols(options.symbolsText)
  const request: StartJobRunRequest = {}
  if (symbols.length) request.symbols = symbols
  if (options.variantIds.length) request.variantIds = [...options.variantIds]
  if (options.skipLlm) request.skipLlm = true
  if (options.dryRun) request.dryRun = true
  return Object.keys(request).length ? request : undefined
}

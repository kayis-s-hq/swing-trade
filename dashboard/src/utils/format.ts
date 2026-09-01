// Shared numeric formatting helpers for financial figures (prices, P&L, percentages,
// quantities). Backend responses or derived calculations (e.g. division-by-zero) can
// produce NaN/Infinity; these helpers guard against rendering those to the user.

const FALLBACK = '—'

/** Returns `value` if it is a finite number, otherwise `undefined`. */
export function safeNumber(value: number | null | undefined): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

/** Formats a currency amount, e.g. "Rs.1,234.56". Falls back to the placeholder on NaN/Infinity. */
export function formatCurrency(
  value: number | null | undefined,
  { prefix = 'Rs.', fallback = FALLBACK }: { prefix?: string; fallback?: string } = {}
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return fallback
  return `${prefix}${safe.toLocaleString('en-US', { maximumFractionDigits: 2 })}`
}

/** Formats a signed currency amount, e.g. "+Rs.1,234.56" / "-Rs.1,234.56". */
export function formatSignedCurrency(
  value: number | null | undefined,
  options: { prefix?: string; fallback?: string } = {}
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return options.fallback ?? FALLBACK
  const sign = safe >= 0 ? '+' : '-'
  return `${sign}${formatCurrency(Math.abs(safe), options)}`
}

/** Formats a percentage, e.g. "12.34%". Falls back to the placeholder on NaN/Infinity. */
export function formatPercent(
  value: number | null | undefined,
  { digits = 2, fallback = FALLBACK }: { digits?: number; fallback?: string } = {}
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return fallback
  return `${safe.toFixed(digits)}%`
}

/** Formats a signed percentage, e.g. "+12.34%" / "-12.34%". */
export function formatSignedPercent(
  value: number | null | undefined,
  options: { digits?: number; fallback?: string } = {}
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return options.fallback ?? FALLBACK
  const sign = safe >= 0 ? '+' : ''
  return `${sign}${formatPercent(safe, options)}`
}

/** Formats a plain decimal number, e.g. "12.34". Falls back to the placeholder on NaN/Infinity. */
export function formatNumber(
  value: number | null | undefined,
  { digits = 2, fallback = FALLBACK }: { digits?: number; fallback?: string } = {}
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return fallback
  return safe.toFixed(digits)
}

/** Formats an integer count with thousands separators, e.g. "1,234". */
export function formatCount(
  value: number | null | undefined,
  fallback: string = FALLBACK
): string {
  const safe = safeNumber(value)
  if (safe === undefined) return fallback
  return safe.toLocaleString()
}

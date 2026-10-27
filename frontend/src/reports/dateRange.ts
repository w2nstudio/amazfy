// The days a report covers, kept in the URL (?days=7, or ?from=…&to=… for custom days) so a reload or a shared link
// shows the same days. Days are the browser's calendar days (time zones per marketplace come later).

export const PRESET_DAYS: readonly number[] = [7, 30, 90]
const DEFAULT_DAYS = 30

/** Both days included, as YYYY-MM-DD. `days` is set for a preset ("last 30 days"), not for custom days. */
export type DateRange = { from: string; to: string; days?: number }

/** YYYY-MM-DD of a local calendar day. */
function isoDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function daysAgo(n: number): string {
  const date = new Date()
  date.setDate(date.getDate() - n)
  return isoDay(date)
}

/** The last `days` days up to yesterday: Amazon has no numbers for today yet. */
export function lastDays(days: number): DateRange {
  return { from: daysAgo(days), to: daysAgo(1), days }
}

const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/

export function rangeFromSearch(params: URLSearchParams): DateRange {
  const from = params.get('from')
  const to = params.get('to')
  if (from && to && ISO_DAY.test(from) && ISO_DAY.test(to)) return { from, to }
  const days = Number(params.get('days'))
  return lastDays(PRESET_DAYS.includes(days) ? days : DEFAULT_DAYS)
}

export function rangeToSearch(range: DateRange): Record<string, string> {
  return range.days ? { days: String(range.days) } : { from: range.from, to: range.to }
}

/**
 * Whether the range reaches into the last 14 days, whose sales still grow: Amazon credits a purchase to an ad up to
 * 14 days after the click (the attribution window).
 */
export function includesRecentDays(range: { to: string }): boolean {
  return range.to >= daysAgo(14) // YYYY-MM-DD strings sort like the days they stand for
}

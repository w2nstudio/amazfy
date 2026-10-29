import { BreakdownRowKind, type BreakdownRow, type CurrencyTotals } from '@/api/generated/model'

// Sorting the drill-down table by a column, in the browser: a level has few rows (the backend has added them up).

export type SortKey =
  'name' | keyof Pick<CurrencyTotals, 'spend' | 'sales' | 'roas' | 'purchases' | 'cpa' | 'conversionRate'>

export type Sort = { key: SortKey; descending: boolean }

/** The first click on a column: numbers biggest first (most spend, best ROAS), names A to Z. */
export function firstSort(key: SortKey): Sort {
  return { key, descending: key !== 'name' }
}

/**
 * The rows in `sort` order, or unchanged without one (the backend's order: by name, special rows after).
 * Currencies are never ranked against each other, missing metrics ("—") come last either way, and the unmatched row
 * stays at the bottom: it is no item the seller can act on, and its spend is unknown.
 */
export function sortRows(rows: BreakdownRow[], sort: Sort | undefined): BreakdownRow[] {
  if (!sort) return rows
  const direction = sort.descending ? -1 : 1
  const unmatched = (row: BreakdownRow) => row.kind === BreakdownRowKind.UNMATCHED
  return [...rows].sort((a, b) => {
    if (unmatched(a) !== unmatched(b)) return unmatched(a) ? 1 : -1
    if (a.totals.currency !== b.totals.currency) return a.totals.currency.localeCompare(b.totals.currency)
    if (sort.key === 'name') return direction * a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
    const x = a.totals[sort.key]
    const y = b.totals[sort.key]
    if (x === undefined || y === undefined) {
      if (x === y) return a.name.localeCompare(b.name)
      return x === undefined ? 1 : -1
    }
    return direction * (x - y) || a.name.localeCompare(b.name)
  })
}

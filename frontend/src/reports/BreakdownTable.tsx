import { keepPreviousData } from '@tanstack/react-query'
import { Fragment } from 'react'
import { errorMessage } from '@/api/client'
import type { BreakdownRow } from '@/api/generated/model'
import { useGetReportBreakdown } from '@/api/generated/reports/reports'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { DateRange } from '@/reports/dateRange'
import { drillInto, LEVEL_NAMES, STARTS, type DrillStep } from '@/reports/drillDown'
import { count, money, percent, ratio } from '@/reports/format'

// The summary split into rows, with drill-down: click a brand to see its products, a product to see its campaigns,
// and so on down to the ads. Without a filter the rows add up to the summary tiles above.
// `path`: the steps from the start to the table shown; the last one is the current table.
export function BreakdownTable({
  accountId,
  range,
  path,
  onPathChange: setPath,
}: {
  accountId: string
  range: DateRange
  path: DrillStep[]
  onPathChange: (path: DrillStep[]) => void
}) {
  const step = path[path.length - 1]
  const breakdown = useGetReportBreakdown(
    accountId,
    { by: step.by, ...step.filter, from: range.from, to: range.to },
    { query: { placeholderData: keepPreviousData } },
  )
  const rows = breakdown.data?.data.rows
  // While the next table loads, the previous rows stay visible (dimmed) but can't be clicked: they belong to another step
  const loading = breakdown.isPlaceholderData

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <nav aria-label="Drill-down path" className="flex flex-wrap items-center gap-1 text-sm">
          {path.map((s, i) => (
            <Fragment key={i}>
              {i > 0 && <span className="text-muted-foreground">›</span>}
              {i < path.length - 1 ? (
                <button
                  type="button"
                  className="underline-offset-4 hover:underline"
                  onClick={() => setPath(path.slice(0, i + 1))}
                >
                  {s.label}
                </button>
              ) : (
                <span className="font-medium">{s.label}</span>
              )}
            </Fragment>
          ))}
        </nav>
        <div className="flex items-center gap-1 text-sm" role="group" aria-label="View by">
          <span className="mr-1 text-muted-foreground">View by</span>
          {STARTS.map((start) => (
            <Button
              key={start.by}
              size="sm"
              variant={path[0].by === start.by ? 'default' : 'outline'}
              onClick={() => setPath([start])}
            >
              {LEVEL_NAMES[start.by]}
            </Button>
          ))}
        </div>
      </div>

      {breakdown.isError ? (
        <FormMessage error>{errorMessage(breakdown.error)}</FormMessage>
      ) : !rows ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No ads with numbers here for these days.</p>
      ) : (
        <div className={cn('overflow-x-auto rounded-lg border transition-opacity', loading && 'opacity-60')}>
          <table className="w-full text-sm">
            <thead className="border-b text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left font-medium">{LEVEL_NAMES[step.by]}</th>
                <Th>Spend</Th>
                <Th>Sales</Th>
                <Th>ROAS</Th>
                <Th>Purchases</Th>
                <Th>CPA</Th>
                <Th>Conv. rate</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const next = drillInto(row, step)
                return (
                  <tr key={`${row.kind}-${row.id ?? ''}-${row.totals.currency}`} className="border-b last:border-0">
                    <td className="max-w-80 px-3 py-2">
                      {next && !loading ? (
                        <button
                          type="button"
                          className="text-left font-medium underline-offset-4 hover:underline"
                          onClick={() => setPath([...path, next])}
                        >
                          {row.name}
                        </button>
                      ) : (
                        <span className="font-medium">{row.name}</span>
                      )}
                      {row.detail && (
                        <span className="block truncate text-xs text-muted-foreground" title={row.detail}>
                          {row.detail}
                        </span>
                      )}
                    </td>
                    <Numbers row={row} />
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Th({ children }: { children: string }) {
  return <th className="px-3 py-2 text-right font-medium whitespace-nowrap">{children}</th>
}

function Numbers({ row: { totals } }: { row: BreakdownRow }) {
  const cells = [
    money(totals.spend, totals.currency),
    money(totals.sales, totals.currency),
    ratio(totals.roas),
    count(totals.purchases),
    money(totals.cpa, totals.currency),
    percent(totals.conversionRate),
  ]
  return cells.map((value, i) => (
    <td key={i} className="px-3 py-2 text-right whitespace-nowrap tabular-nums">
      {value}
    </td>
  ))
}

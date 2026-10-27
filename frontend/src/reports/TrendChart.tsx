import { keepPreviousData } from '@tanstack/react-query'
import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { errorMessage } from '@/api/client'
import type { TrendDay, TrendSeries } from '@/api/generated/model'
import { useGetReportTrend } from '@/api/generated/reports/reports'
import { FormMessage } from '@/components/AuthCard'
import { cn } from '@/lib/utils'
import { stillCountingFrom, type DateRange } from '@/reports/dateRange'
import type { DrillFilter } from '@/reports/drillDown'
import { count, money, ratio } from '@/reports/format'

// Spend and sales per day (requirement A13) for the whole seller account or the drill-down row being looked at.
// A plain SVG line chart: both series are money, so they share one axis. Hover (or focus and use the arrow keys) to
// read a day's numbers; the "Daily numbers" table below the chart has them all.

const SERIES = [
  { key: 'sales', label: 'Sales', color: 'var(--chart-1)' },
  { key: 'spend', label: 'Spend', color: 'var(--chart-2)' },
] as const

const HEIGHT = 240
const PAD = { top: 20, right: 12, bottom: 28, left: 64 }

export function TrendChart({
  accountId,
  range,
  filter,
  title,
}: {
  accountId: string
  range: DateRange
  filter: DrillFilter
  title: string
}) {
  const trend = useGetReportTrend(
    accountId,
    { ...filter, from: range.from, to: range.to },
    { query: { placeholderData: keepPreviousData } },
  )
  const series = trend.data?.data.series

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-sm font-medium">Per day · {title}</h3>
        <Legend />
      </div>
      {trend.isError ? (
        <FormMessage error>{errorMessage(trend.error)}</FormMessage>
      ) : !series ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : series.length === 0 ? (
        <p className="text-sm text-muted-foreground">No numbers for these days.</p>
      ) : (
        <div className={cn('flex flex-col gap-6 transition-opacity', trend.isPlaceholderData && 'opacity-60')}>
          {/* Amounts are never mixed across currencies: one chart each */}
          {series.map((s) => (
            <div key={s.currency} className="flex flex-col gap-2">
              {series.length > 1 && <span className="text-xs font-medium text-muted-foreground">{s.currency}</span>}
              <LineChart series={s} />
              <DailyTable series={s} />
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function Legend() {
  return (
    <div className="flex gap-4 text-xs text-muted-foreground">
      {SERIES.map((s) => (
        <span key={s.key} className="flex items-center gap-1.5">
          <span className="inline-block h-0.5 w-4 rounded-full" style={{ background: s.color }} />
          {s.label}
        </span>
      ))}
    </div>
  )
}

function LineChart({ series }: { series: TrendSeries }) {
  const [box, width] = useWidth()
  const [active, setActive] = useState<number>()
  const { days, currency } = series

  const ticks = niceTicks(Math.max(0, ...days.flatMap((d) => [d.totals.spend, d.totals.sales])))
  const top = ticks[ticks.length - 1] || 1
  const plotWidth = Math.max(0, width - PAD.left - PAD.right)
  const plotHeight = HEIGHT - PAD.top - PAD.bottom
  const step = days.length > 1 ? plotWidth / (days.length - 1) : 0
  const x = (i: number) => PAD.left + (days.length > 1 ? i * step : plotWidth / 2)
  const y = (value: number) => PAD.top + plotHeight - (value / top) * plotHeight
  const line = (key: 'sales' | 'spend') =>
    days.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(d.totals[key]).toFixed(1)}`).join('')
  const axisMoney = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  })

  // The last 14 days: Amazon may still credit purchases to them, so their sales will grow
  const counting = days.findIndex((d) => d.date >= stillCountingFrom())

  function pick(event: PointerEvent<SVGRectElement>) {
    const left = event.currentTarget.getBoundingClientRect().left
    const i = step ? Math.round((event.clientX - left) / step) : 0
    setActive(Math.min(days.length - 1, Math.max(0, i)))
  }

  function move(event: KeyboardEvent<SVGSVGElement>) {
    const by = { ArrowLeft: -1, ArrowRight: 1 }[event.key]
    if (by === undefined) return
    event.preventDefault()
    setActive((i) => Math.min(days.length - 1, Math.max(0, (i ?? days.length - 1) + by)))
  }

  return (
    <div ref={box} className="relative">
      {width > 0 && (
        <svg
          width={width}
          height={HEIGHT}
          role="img"
          aria-label={`Sales and spend per day in ${currency}; use the arrow keys to read each day`}
          tabIndex={0}
          className="block rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring"
          onKeyDown={move}
          onFocus={() => setActive((i) => i ?? days.length - 1)}
          onBlur={() => setActive(undefined)}
        >
          {counting >= 0 && (
            <g>
              <rect
                x={x(counting)}
                y={PAD.top}
                width={PAD.left + plotWidth - x(counting)}
                height={plotHeight}
                className="fill-muted"
              />
              <text
                x={PAD.left + plotWidth - 4}
                y={PAD.top - 6}
                textAnchor="end"
                className="fill-muted-foreground text-[11px]"
              >
                Sales still being counted
              </text>
            </g>
          )}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={PAD.left + plotWidth} y1={y(t)} y2={y(t)} className="stroke-border" />
              <text
                x={PAD.left - 8}
                y={y(t)}
                dy="0.32em"
                textAnchor="end"
                className="fill-muted-foreground text-[11px]"
              >
                {axisMoney.format(t)}
              </text>
            </g>
          ))}
          {dateTicks(days.length, plotWidth).map((i) => (
            <text
              key={i}
              x={x(i)}
              y={HEIGHT - 8}
              textAnchor={i === 0 ? 'start' : i === days.length - 1 ? 'end' : 'middle'}
              className="fill-muted-foreground text-[11px]"
            >
              {shortDate(days[i].date)}
            </text>
          ))}
          {active !== undefined && (
            <line
              x1={x(active)}
              x2={x(active)}
              y1={PAD.top}
              y2={PAD.top + plotHeight}
              className="stroke-foreground/40"
            />
          )}
          {SERIES.map((s) => (
            <path
              key={s.key}
              d={line(s.key)}
              fill="none"
              stroke={s.color}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {active !== undefined &&
            SERIES.map((s) => (
              <circle
                key={s.key}
                cx={x(active)}
                cy={y(days[active].totals[s.key])}
                r={4}
                fill={s.color}
                className="stroke-background"
                strokeWidth={2}
              />
            ))}
          {/* Hit area: the whole plot, so the reader aims at a day, not at a 2px line */}
          <rect
            x={PAD.left - step / 2}
            y={PAD.top}
            width={plotWidth + step}
            height={plotHeight}
            fill="transparent"
            onPointerMove={pick}
            onPointerLeave={() => setActive(undefined)}
          />
        </svg>
      )}
      {active !== undefined && (
        <Tooltip day={days[active]} currency={currency} x={x(active)} flip={x(active) > width / 2} />
      )}
    </div>
  )
}

function Tooltip({ day, currency, x, flip }: { day: TrendDay; currency: string; x: number; flip: boolean }) {
  return (
    <div
      className="pointer-events-none absolute top-2 flex min-w-36 flex-col gap-1 rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md"
      style={{ left: x, transform: flip ? 'translateX(calc(-100% - 12px))' : 'translateX(12px)' }}
    >
      <span className="text-muted-foreground">{shortDate(day.date, true)}</span>
      {SERIES.map((s) => (
        <span key={s.key} className="flex items-center gap-2">
          <span className="inline-block h-0.5 w-3 rounded-full" style={{ background: s.color }} />
          <strong className="font-medium tabular-nums">{money(day.totals[s.key], currency)}</strong>
          <span className="text-muted-foreground">{s.label}</span>
        </span>
      ))}
      <span className="text-muted-foreground">
        ROAS {ratio(day.totals.roas)} · {count(day.totals.purchases)} purchases
      </span>
    </div>
  )
}

function DailyTable({ series: { days, currency } }: { series: TrendSeries }) {
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-xs text-muted-foreground">Daily numbers</summary>
      <div className="mt-2 max-h-72 overflow-auto rounded-lg border">
        <table className="w-full">
          <thead className="sticky top-0 border-b bg-background text-muted-foreground">
            <tr>
              <th className="px-3 py-2 text-left font-medium">Day</th>
              <th className="px-3 py-2 text-right font-medium">Spend</th>
              <th className="px-3 py-2 text-right font-medium">Sales</th>
              <th className="px-3 py-2 text-right font-medium">ROAS</th>
              <th className="px-3 py-2 text-right font-medium">Purchases</th>
            </tr>
          </thead>
          <tbody className="tabular-nums">
            {days.map((d) => (
              <tr key={d.date} className="border-b last:border-0">
                <td className="px-3 py-1.5">{shortDate(d.date, true)}</td>
                <td className="px-3 py-1.5 text-right">{money(d.totals.spend, currency)}</td>
                <td className="px-3 py-1.5 text-right">{money(d.totals.sales, currency)}</td>
                <td className="px-3 py-1.5 text-right">{ratio(d.totals.roas)}</td>
                <td className="px-3 py-1.5 text-right">{count(d.totals.purchases)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}

/** The element's width, kept up to date as the window resizes. */
function useWidth() {
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    if (!ref.current) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}

/** 0 and about four round steps up to at least `max`, e.g. 0 / 50 / 100 / 150 / 200. */
function niceTicks(max: number): number[] {
  if (max <= 0) return [0]
  const raw = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw
  const ticks = [0]
  while (ticks[ticks.length - 1] < max) ticks.push(ticks.length * step)
  return ticks
}

/** Which days get a date under the axis: first, last and evenly spread ones, at least ~80px apart. */
function dateTicks(days: number, plotWidth: number): number[] {
  const labels = Math.min(days, Math.max(2, Math.floor(plotWidth / 80)))
  if (days <= 1) return [0]
  return [...new Set(Array.from({ length: labels }, (_, k) => Math.round((k * (days - 1)) / (labels - 1))))]
}

/** "Oct 3", or with the weekday "Fri, Oct 3" (YYYY-MM-DD read as a local calendar day) */
function shortDate(iso: string, withWeekday = false): string {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    ...(withWeekday && { weekday: 'short' }),
  })
}

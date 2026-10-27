import { keepPreviousData } from '@tanstack/react-query'
import { Link } from 'react-router'
import { errorMessage } from '@/api/client'
import type { CurrencyTotals } from '@/api/generated/model'
import { useGetReportSummary } from '@/api/generated/reports/reports'
import { FormMessage } from '@/components/AuthCard'
import { cn } from '@/lib/utils'
import { includesRecentDays, type DateRange } from '@/reports/dateRange'
import { count, money, percent, ratio } from '@/reports/format'

// The seller account's totals for the chosen days (requirement A11): Google spend against the Amazon sales it brought.
// Amounts are never added up across currencies, so a seller account with GBP and EUR gets one block per currency.
export function ReportSummary({ accountId, range }: { accountId: string; range: DateRange }) {
  const summary = useGetReportSummary(
    accountId,
    { from: range.from, to: range.to },
    // Keep showing the previous days' numbers (dimmed) while the new ones load, instead of an empty page
    { query: { placeholderData: keepPreviousData } },
  )
  const report = summary.data?.data

  if (summary.isError) return <FormMessage error>{errorMessage(summary.error)}</FormMessage>
  if (!report) return <p className="text-sm text-muted-foreground">Loading…</p>
  if (report.currencies.length === 0) return <NoNumbers />

  return (
    <section className={cn('flex flex-col gap-6 transition-opacity', summary.isPlaceholderData && 'opacity-60')}>
      {report.currencies.map((totals) => (
        <CurrencyTiles key={totals.currency} totals={totals} showCurrency={report.currencies.length > 1} />
      ))}
      {includesRecentDays(report) && (
        <p className="text-xs text-muted-foreground">
          Sales of the last 14 days still grow: Amazon credits a purchase to an ad up to 14 days after the click.
        </p>
      )}
    </section>
  )
}

function CurrencyTiles({ totals, showCurrency }: { totals: CurrencyTotals; showCurrency: boolean }) {
  const amount = (value: number | undefined) => money(value, totals.currency)

  return (
    <div className="flex flex-col gap-2">
      {showCurrency && <h3 className="text-sm font-medium">{totals.currency}</h3>}
      {/* As many tiles per row as fit at 11rem each: six on a wide screen, fewer on smaller ones */}
      <div className="grid grid-cols-[repeat(auto-fit,minmax(11rem,1fr))] gap-3">
        <Tile label="Spend" value={amount(totals.spend)} hint={`Google Ads · ${count(totals.clicks)} clicks`} />
        <Tile label="Sales" value={amount(totals.sales)} hint={`Amazon · ${count(totals.unitsSold)} units`} />
        <Tile label="ROAS" value={ratio(totals.roas)} hint="Sales ÷ spend" />
        <Tile
          label="Purchases"
          value={count(totals.purchases)}
          hint={`${count(totals.clickThroughs)} visits to Amazon`}
        />
        <Tile label="CPA" value={amount(totals.cpa)} hint="Spend ÷ purchases" />
        <Tile label="Conversion rate" value={percent(totals.conversionRate)} hint="Purchases ÷ visits" />
      </div>
    </div>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border p-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      {/* Wraps inside the tile if an amount is ever too long, instead of crossing its border */}
      <span className="text-2xl font-medium [overflow-wrap:anywhere] tabular-nums">{value}</span>
      <span className="text-xs text-muted-foreground">{hint}</span>
    </div>
  )
}

function NoNumbers() {
  return (
    <section className="flex flex-col gap-1 rounded-lg border p-4 text-sm">
      <p className="font-medium">No numbers for these days</p>
      <p className="text-muted-foreground">
        Numbers appear once your Google Ads and Amazon Attribution are connected. Until then, make sure your{' '}
        <Link to="/brands" className="underline underline-offset-4">
          brands
        </Link>{' '}
        and{' '}
        <Link to="/products" className="underline underline-offset-4">
          products
        </Link>{' '}
        are complete: ads are matched to them.
      </p>
    </section>
  )
}

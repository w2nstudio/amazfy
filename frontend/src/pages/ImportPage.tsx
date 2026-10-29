import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { AMAZON_SITES, regionMarketplaces } from '@/account/amazon'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import { useImportAttributionCsv } from '@/api/generated/attribution/attribution'
import { Role, type AttributionImportResult, type Marketplace, type Region } from '@/api/generated/model'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { count, money } from '@/reports/format'

// Upload an Amazon Attribution report (CSV) for one marketplace (requirement A10). The file is first read as a
// preview that saves nothing; the seller checks the numbers and then imports. A valid file can still be the wrong one
// (another seller account, another marketplace), and the preview's numbers are where that shows.

/** Below this share of lines matching the account's Google ads, the preview warns the file may be the wrong one. */
const LOW_MATCH_SHARE = 0.5

export function ImportPage() {
  const { current } = useCurrentAccount()
  if (!current) return null
  // key: switching accounts starts fresh (a file chosen for one account is never imported into another)
  return (
    <Import
      key={current.account.id}
      accountId={current.account.id}
      accountName={current.account.name}
      region={current.account.region}
      canEdit={current.role !== Role.VIEWER}
    />
  )
}

function Import({
  accountId,
  accountName,
  region,
  canEdit,
}: {
  accountId: string
  accountName: string
  region: Region
  canEdit: boolean
}) {
  const marketplaces = regionMarketplaces(region)
  const [marketplace, setMarketplace] = useState<Marketplace>(marketplaces[0])
  const [file, setFile] = useState<File>()
  const [inputKey, setInputKey] = useState(0) // a new key empties the file input
  const preview = useImportAttributionCsv()
  const save = useImportAttributionCsv()
  const queryClient = useQueryClient()

  function readPreview(chosen: File | undefined, site: Marketplace) {
    save.reset()
    if (chosen) preview.mutate({ accountId, data: { file: chosen, marketplace: site }, params: { dryRun: true } })
    else preview.reset()
  }

  function startOver() {
    setFile(undefined)
    setInputKey((k) => k + 1)
    preview.reset()
    save.reset()
  }

  function importFile() {
    if (!file) return
    save.mutate(
      { accountId, data: { file, marketplace } },
      { onSuccess: () => queryClient.invalidateQueries() }, // the dashboard's numbers changed
    )
  }

  const saved = save.data?.data
  const previewed = preview.data?.data

  return (
    <main className="flex flex-col gap-6">
      <section className="flex flex-col gap-1">
        <h2 className="text-xl font-medium">Import Amazon Attribution report</h2>
        <p className="max-w-prose text-sm text-muted-foreground">
          Download a report as CSV from the Amazon Attribution console and upload it here, one marketplace at a time.
          Amazfy links each line to your Google ad with the same campaign, ad group and ad ids. Uploading the same days
          again updates them; it never counts them twice.
        </p>
      </section>

      {!canEdit ? (
        <p className="text-sm text-muted-foreground">
          Only owners and editors of this seller account can import reports.
        </p>
      ) : saved ? (
        <section className="flex flex-col gap-3 rounded-lg border p-4 text-sm">
          <p className="font-medium">
            Imported {count(saved.lines)} lines for {day(saved.fromDate)} – {day(saved.toDate)}.
          </p>
          <p className="text-muted-foreground">
            {money(saved.sales, saved.currency)} in sales; {count(saved.matchedLines)} lines linked to your ads,{' '}
            {count(saved.unmatchedLines)} shown as "Not matched to an ad".
          </p>
          <div className="flex gap-2">
            <Button asChild size="sm">
              <Link to="/">See the dashboard</Link>
            </Button>
            <Button size="sm" variant="outline" onClick={startOver}>
              Import another report
            </Button>
          </div>
        </section>
      ) : (
        <>
          <section className="flex flex-wrap items-end gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="import-marketplace">Marketplace</Label>
              <Select
                value={marketplace}
                onValueChange={(value) => {
                  setMarketplace(value as Marketplace)
                  readPreview(file, value as Marketplace)
                }}
              >
                <SelectTrigger id="import-marketplace" className="w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {marketplaces.map((m) => (
                    <SelectItem key={m} value={m}>
                      {AMAZON_SITES[m]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="import-file">Report (CSV)</Label>
              <Input
                key={inputKey}
                id="import-file"
                type="file"
                accept=".csv,text/csv"
                className="w-72"
                onChange={(e) => {
                  const chosen = e.target.files?.[0]
                  setFile(chosen)
                  readPreview(chosen, marketplace)
                }}
              />
            </div>
          </section>

          {preview.isPending && <p className="text-sm text-muted-foreground">Reading the file…</p>}
          <FormMessage error>{errorMessage(preview.error)}</FormMessage>

          {previewed && file && (
            <Preview
              result={previewed}
              fileName={file.name}
              site={AMAZON_SITES[marketplace]}
              accountName={accountName}
              importing={save.isPending}
              onImport={importFile}
              onCancel={startOver}
            />
          )}
          <FormMessage error>{errorMessage(save.error)}</FormMessage>
        </>
      )}
    </main>
  )
}

function Preview({
  result,
  fileName,
  site,
  accountName,
  importing,
  onImport,
  onCancel,
}: {
  result: AttributionImportResult
  fileName: string
  site: string
  accountName: string
  importing: boolean
  onImport: () => void
  onCancel: () => void
}) {
  const lowMatch = result.lines > 0 && result.matchedLines / result.lines < LOW_MATCH_SHARE

  return (
    <section className="flex flex-col gap-3 rounded-lg border p-4 text-sm" aria-label="Preview">
      <p className="font-medium">
        {fileName}: {day(result.fromDate)} – {day(result.toDate)}, {site}
      </p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1">
        <dt className="text-muted-foreground">Google Ads lines</dt>
        <dd className="tabular-nums">{count(result.lines)}</dd>
        <dt className="text-muted-foreground">Linked to your ads</dt>
        <dd className="tabular-nums">
          {count(result.matchedLines)} ({percentOf(result.matchedLines, result.lines)})
        </dd>
        <dt className="text-muted-foreground">Not matched to an ad</dt>
        <dd className="tabular-nums">{count(result.unmatchedLines)}</dd>
        <dt className="text-muted-foreground">Sales</dt>
        <dd className="tabular-nums">{money(result.sales, result.currency)}</dd>
        {result.otherPublisherLines > 0 && (
          <>
            <dt className="text-muted-foreground">Other publishers</dt>
            <dd className="tabular-nums">
              {count(result.otherPublisherLines)} lines left out (Amazfy measures Google Ads)
            </dd>
          </>
        )}
      </dl>
      {lowMatch && (
        <p role="alert" className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2">
          <strong>Most lines don't match your Google ads.</strong> Is this report from {accountName}, and for {site}? If
          it is, Amazfy may not know these ads yet; their sales are still imported, as "Not matched to an ad".
        </p>
      )}
      <div className="flex gap-2">
        <Button size="sm" onClick={onImport} disabled={importing}>
          {importing ? 'Importing…' : `Import ${count(result.lines)} lines`}
        </Button>
        <Button size="sm" variant="outline" onClick={onCancel} disabled={importing}>
          Choose another file
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">Nothing is saved until you import.</p>
    </section>
  )
}

function percentOf(part: number, whole: number) {
  return whole === 0 ? '0%' : `${Math.round((part / whole) * 100)}%`
}

/** "Oct 27, 2026" for a YYYY-MM-DD day */
function day(iso: string) {
  const [year, month, date] = iso.split('-').map(Number)
  return new Date(year, month - 1, date).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

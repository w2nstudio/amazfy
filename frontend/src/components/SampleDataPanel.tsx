import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { errorMessage } from '@/api/client'
import { useCreateSampleData } from '@/api/generated/development/development'
import { SampleDataSet } from '@/api/generated/model'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { rangeToSearch } from '@/reports/dateRange'

// Local development only: fills the seller account with sample ads, spend and Attribution numbers (see the backend's
// SampleDataService and docs/guides/sample-data.md). Shown only in the dev build (`npm run dev`), never in a production
// build; the backend endpoint exists only on local servers too.

/** Small demo first: it's the one used most often. */
const ORDER = [SampleDataSet.SMALL_DEMO, SampleDataSet.FULL]

const SETS: Record<SampleDataSet, { button: string; name: string; text: string }> = {
  SMALL_DEMO: {
    button: 'Small demo: Leafwell Garden, 4 days',
    name: 'small demo (Leafwell Garden, 4 days)',
    text: 'One gardening brand, two products and four ads over 4 days, uneven like a real shop. For demos; the dashboard then shows exactly those days.',
  },
  FULL: {
    button: 'Full sample: 90 days',
    name: 'full sample (90 days)',
    text: 'Three aquarium products over 90 days up to yesterday, round numbers that are the same every day. For checking reports with a calculator.',
  },
}

export function SampleDataPanel({ accountId }: { accountId: string }) {
  const create = useCreateSampleData()
  const queryClient = useQueryClient()
  const [, setSearchParams] = useSearchParams()
  const [confirming, setConfirming] = useState<SampleDataSet | null>(null)
  const result = create.data?.data

  function fill(set: SampleDataSet) {
    create.mutate(
      { accountId, params: { set } },
      {
        onSuccess: async (response) => {
          setConfirming(null)
          // The small demo's days lie in the past: show exactly them. The full sample fits the default last 30 days.
          if (set === SampleDataSet.SMALL_DEMO) {
            const { fromDate, toDate } = response.data
            setSearchParams(rangeToSearch({ from: fromDate, to: toDate }))
          }
          await queryClient.invalidateQueries() // brands and products may have been added
        },
      },
    )
  }

  return (
    <section className="flex flex-col gap-2 rounded-lg border border-dashed p-4 text-sm">
      <h3 className="font-medium">Development: sample data</h3>
      <p className="text-muted-foreground">
        Replaces this seller account&apos;s ads and numbers with made-up ones, for sample products added if missing;
        your own products get no ads. docs/guides/sample-data.md lists what each report should show.
      </p>
      <ul className="flex flex-col gap-1 text-muted-foreground">
        {ORDER.map((set) => (
          <li key={set}>
            <span className="font-medium text-foreground">{SETS[set].button}.</span> {SETS[set].text}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-2">
        {confirming ? (
          <>
            <span>Replace this seller account&apos;s ads and numbers with the {SETS[confirming].name}?</span>
            <Button size="sm" onClick={() => fill(confirming)} disabled={create.isPending}>
              {create.isPending ? 'Filling…' : 'Yes, fill'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(null)}>
              Cancel
            </Button>
          </>
        ) : (
          ORDER.map((set) => (
            <Button key={set} size="sm" variant="outline" onClick={() => setConfirming(set)}>
              {SETS[set].button}
            </Button>
          ))
        )}
      </div>
      <FormMessage>
        {result &&
          `Done: ${result.campaigns} campaigns, ${result.ads} ads for ${result.products} products, ` +
            `${result.fromDate} to ${result.toDate}.`}
      </FormMessage>
      <FormMessage error>{errorMessage(create.error)}</FormMessage>
    </section>
  )
}

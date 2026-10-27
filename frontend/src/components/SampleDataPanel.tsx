import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '@/api/client'
import { useCreateSampleData } from '@/api/generated/development/development'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Local development only: fills the seller account with sample ads, spend and Attribution numbers (see the backend's
// SampleDataService and docs/guides/sample-data.md). Shown only in the dev build (`npm run dev`), never in a production build; the backend endpoint
// exists only on local servers too.
export function SampleDataPanel({ accountId }: { accountId: string }) {
  const create = useCreateSampleData()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const result = create.data?.data

  function fill() {
    create.mutate(
      { accountId },
      {
        onSuccess: async () => {
          setConfirming(false)
          await queryClient.invalidateQueries() // brands and products may have been added
        },
      },
    )
  }

  return (
    <section className="flex flex-col gap-2 rounded-lg border border-dashed p-4 text-sm">
      <h3 className="font-medium">Development: sample data</h3>
      <p className="text-muted-foreground">
        Fills this seller account with 90 days (up to yesterday) of made-up Google ads, spend and Attribution numbers
        for three sample products, added if missing; your own products get no ads. Round numbers, the same every day:
        docs/guides/sample-data.md lists what each report should show. Replaces earlier sample data.
      </p>
      <div className="flex items-center gap-2">
        {confirming ? (
          <>
            <span>Replace this seller account&apos;s ads and numbers?</span>
            <Button size="sm" onClick={fill} disabled={create.isPending}>
              {create.isPending ? 'Filling…' : 'Yes, fill'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(false)}>
              Cancel
            </Button>
          </>
        ) : (
          <Button size="sm" variant="outline" onClick={() => setConfirming(true)}>
            Fill with sample data
          </Button>
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

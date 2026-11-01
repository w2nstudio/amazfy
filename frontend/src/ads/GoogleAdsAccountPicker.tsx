import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { accountLabel } from '@/ads/accountLabel'
import { errorMessage } from '@/api/client'
import { useChooseGoogleAdsAccount, useListGoogleAdsAccounts } from '@/api/generated/google-ads/google-ads'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

// One Google login can reach several Google Ads accounts; the owner chooses the one advertising this seller account.
export function GoogleAdsAccountPicker({
  accountId,
  chosen,
  onDone,
}: {
  accountId: string
  chosen?: string
  onDone: () => void
}) {
  const accounts = useListGoogleAdsAccounts(accountId, { query: { retry: false } })
  const choose = useChooseGoogleAdsAccount()
  const queryClient = useQueryClient()
  const [choice, setChoice] = useState(chosen ?? '')
  const list = accounts.data?.data

  function save() {
    choose.mutate(
      { accountId, data: { customerId: choice } },
      {
        onSuccess: () => {
          onDone()
          return queryClient.invalidateQueries()
        },
      },
    )
  }

  if (accounts.isError) return <FormMessage error>{errorMessage(accounts.error)}</FormMessage>
  if (!list) return <p className="text-muted-foreground">Asking Google which accounts you can reach…</p>
  if (list.length === 0) {
    return (
      <p className="max-w-prose text-muted-foreground">
        This Google login can't reach any Google Ads account with campaigns. Connect again with the Google login that
        has access to your Google Ads account.
      </p>
    )
  }
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select value={choice} onValueChange={setChoice}>
        <SelectTrigger className="w-80" aria-label="Google Ads account">
          <SelectValue placeholder="Choose a Google Ads account" />
        </SelectTrigger>
        <SelectContent>
          {list.map((account) => (
            <SelectItem key={account.customerId} value={account.customerId}>
              {accountLabel(account)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Button size="sm" onClick={save} disabled={!choice || choose.isPending}>
        {choose.isPending ? 'Saving…' : 'Use this account'}
      </Button>
      <FormMessage error>{errorMessage(choose.error)}</FormMessage>
    </div>
  )
}

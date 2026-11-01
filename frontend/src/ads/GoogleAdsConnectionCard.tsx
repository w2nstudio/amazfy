import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { accountLabel } from '@/ads/accountLabel'
import { GoogleAdsAccountPicker } from '@/ads/GoogleAdsAccountPicker'
import { errorMessage } from '@/api/client'
import {
  useDisconnectGoogleAds,
  useGetGoogleAdsConnection,
  useStartGoogleAdsConnect,
  useSyncGoogleAds,
} from '@/api/generated/google-ads/google-ads'
import type { GoogleAdsSyncResult } from '@/api/generated/model'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Whether this seller account has allowed Amazfy to read its Google Ads (requirement A4). Connecting takes the browser
// to Google's consent page; the backend sends it back here with ?googleAds=connected or =failed. Only owners can change
// the connection.

function count(number: number, one: string, many: string) {
  return `${number} ${number === 1 ? one : many}`
}

function syncSummary(result: GoogleAdsSyncResult) {
  const found = `Found ${count(result.campaigns, 'Search campaign', 'Search campaigns')}, ${count(result.adGroups, 'ad group', 'ad groups')} and ${count(result.ads, 'ad', 'ads')}.`
  const linked = result.linked > 0 ? ` Linked ${count(result.linked, 'ad', 'ads')} automatically.` : ''
  return `${found}${linked} ${count(result.stillUnlinked, 'ad', 'ads')} left to link.`
}

export function GoogleAdsConnectionCard({
  accountId,
  canEdit,
  canManage,
}: {
  accountId: string
  canEdit: boolean
  canManage: boolean
}) {
  const connection = useGetGoogleAdsConnection(accountId)
  const start = useStartGoogleAdsConnect()
  const disconnect = useDisconnectGoogleAds()
  const sync = useSyncGoogleAds()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [confirming, setConfirming] = useState(false)
  const [changingAccount, setChangingAccount] = useState(false)
  const status = connection.data?.data
  const outcome = searchParams.get('googleAds')

  function connect() {
    start.mutate({ accountId }, { onSuccess: (response) => window.location.assign(response.data.authorizationUrl) })
  }

  function bringInCampaigns() {
    // Everything may change: this card, the ads to link and the dashboard's rows
    sync.mutate({ accountId }, { onSuccess: () => queryClient.invalidateQueries() })
  }

  function confirmDisconnect() {
    disconnect.mutate(
      { accountId },
      {
        onSuccess: () => {
          setConfirming(false)
          setSearchParams({}, { replace: true }) // an earlier "connected" no longer applies
          return queryClient.invalidateQueries()
        },
      },
    )
  }

  return (
    <section className="flex flex-col gap-3 rounded-lg border p-4 text-sm">
      <h2 className="text-base font-medium">Google Ads</h2>

      {connection.isError ? (
        <FormMessage error>{errorMessage(connection.error)}</FormMessage>
      ) : !status ? (
        <p className="text-muted-foreground">Loading…</p>
      ) : status.connected ? (
        <p>
          Connected{status.connectedAt && ` on ${new Date(status.connectedAt).toLocaleDateString()}`}.{' '}
          {status.account ? (
            <>
              Amazfy reads <span className="font-medium">{accountLabel(status.account)}</span>.{' '}
              {status.syncedAt
                ? `Campaigns last brought in on ${new Date(status.syncedAt).toLocaleString()}.`
                : 'No campaigns brought in from it yet.'}
            </>
          ) : (
            'Choose which Google Ads account Amazfy should read.'
          )}
        </p>
      ) : (
        <p className="max-w-prose text-muted-foreground">
          Not connected. Connect it so Amazfy can read your campaigns, ads and what they cost. You allow this on
          Google's page, so Amazfy never sees your Google password.
        </p>
      )}

      {outcome === 'connected' && status?.connected && <FormMessage>Google Ads is connected.</FormMessage>}
      {outcome === 'failed' && (
        <FormMessage error>
          Google Ads couldn't be connected. Please try again, and allow access to Google Ads on Google's page.
        </FormMessage>
      )}
      {disconnect.isSuccess && (
        <FormMessage>
          Disconnected: Amazfy deleted its access, and numbers already brought in stay. To remove Amazfy from your
          Google login too, use{' '}
          <a
            href="https://myaccount.google.com/connections"
            target="_blank"
            rel="noreferrer"
            className="underline underline-offset-4"
          >
            your Google Account's connections
          </a>
          .
        </FormMessage>
      )}
      {sync.data && !sync.isPending && <FormMessage>{syncSummary(sync.data.data)}</FormMessage>}
      <FormMessage error>{errorMessage(start.error ?? disconnect.error ?? sync.error)}</FormMessage>

      {status?.account && canEdit && !confirming && !changingAccount && (
        <div className="flex flex-wrap items-center gap-3">
          <Button size="sm" onClick={bringInCampaigns} disabled={sync.isPending}>
            {sync.isPending ? 'Bringing in campaigns…' : 'Bring in campaigns'}
          </Button>
          {status.syncedAt && (
            <Link to="/campaigns" className="underline underline-offset-4">
              See campaigns
            </Link>
          )}
        </div>
      )}

      {status &&
        (!canManage ? (
          <p className="text-muted-foreground">Only owners of this seller account can change this connection.</p>
        ) : !status.connected ? (
          <div className="flex flex-col items-start gap-2">
            <Button size="sm" onClick={connect} disabled={start.isPending}>
              {start.isPending ? 'Opening Google…' : 'Connect Google Ads'}
            </Button>
            <p className="max-w-prose text-muted-foreground">
              Google asks for a passkey each time you connect. If your Google login doesn't have one, Google will ask
              you to create it, and a new passkey can take up to 7 days to start working.
            </p>
          </div>
        ) : confirming ? (
          <div className="flex flex-wrap items-center gap-2">
            <span>Disconnect Google Ads? Amazfy will stop being able to read it.</span>
            <Button size="sm" variant="destructive" onClick={confirmDisconnect} disabled={disconnect.isPending}>
              {disconnect.isPending ? 'Disconnecting…' : 'Disconnect'}
            </Button>
            <Button size="sm" variant="outline" onClick={() => setConfirming(false)}>
              Keep connected
            </Button>
          </div>
        ) : (
          <div className="flex flex-col items-start gap-3">
            {(!status.account || changingAccount) && (
              <GoogleAdsAccountPicker
                accountId={accountId}
                chosen={status.account?.customerId}
                onDone={() => setChangingAccount(false)}
              />
            )}
            <div className="flex flex-wrap items-center gap-2">
              {status.account && !changingAccount && (
                <Button size="sm" variant="outline" onClick={() => setChangingAccount(true)}>
                  Change account
                </Button>
              )}
              <Button size="sm" variant="outline" onClick={connect} disabled={start.isPending}>
                {start.isPending ? 'Opening Google…' : 'Connect again'}
              </Button>
              <Button size="sm" variant="outline" onClick={() => setConfirming(true)}>
                Disconnect
              </Button>
            </div>
          </div>
        ))}
    </section>
  )
}

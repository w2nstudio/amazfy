import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { errorMessage } from '@/api/client'
import {
  useDisconnectGoogleAds,
  useGetGoogleAdsConnection,
  useStartGoogleAdsConnect,
} from '@/api/generated/google-ads/google-ads'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Whether this seller account has allowed Amazfy to read its Google Ads (requirement A4). Connecting takes the browser
// to Google's consent page; the backend sends it back here with ?googleAds=connected or =failed. Only owners can change
// the connection.

export function GoogleAdsConnectionCard({ accountId, canManage }: { accountId: string; canManage: boolean }) {
  const connection = useGetGoogleAdsConnection(accountId)
  const start = useStartGoogleAdsConnect()
  const disconnect = useDisconnectGoogleAds()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [confirming, setConfirming] = useState(false)
  const status = connection.data?.data
  const outcome = searchParams.get('googleAds')

  function connect() {
    start.mutate({ accountId }, { onSuccess: (response) => window.location.assign(response.data.authorizationUrl) })
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
          Connected{status.connectedAt && ` on ${new Date(status.connectedAt).toLocaleDateString()}`}. Amazfy has
          permission to read this seller account's Google Ads.
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
      <FormMessage error>{errorMessage(start.error ?? disconnect.error)}</FormMessage>

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
          <div>
            <Button size="sm" variant="outline" onClick={() => setConfirming(true)}>
              Disconnect
            </Button>
          </div>
        ))}
    </section>
  )
}

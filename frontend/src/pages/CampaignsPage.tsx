import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { accountNumber } from '@/ads/accountLabel'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import { useListCampaignAdGroups, useListCampaigns } from '@/api/generated/ads/ads'
import { useGetGoogleAdsConnection } from '@/api/generated/google-ads/google-ads'
import { ChannelStatus, type Campaign, type CampaignAd, type GoogleAdsAccount } from '@/api/generated/model'
import { FormMessage } from '@/components/AuthCard'
import { cn } from '@/lib/utils'
import { count, money } from '@/reports/format'

// The current seller account's campaigns, ad groups and ads (requirement A6). A campaign's ad groups and ads load when
// it's opened, because a large seller has thousands. Read-only: changes are made on Google, and links on the Ads page.

export function CampaignsPage() {
  const { current } = useCurrentAccount()
  if (!current) return null
  // key: switching accounts starts fresh, with every campaign closed
  return <Campaigns key={current.account.id} accountId={current.account.id} />
}

function Campaigns({ accountId }: { accountId: string }) {
  const campaigns = useListCampaigns(accountId)
  const connection = useGetGoogleAdsConnection(accountId)
  const list = campaigns.data?.data
  const chosen = connection.data?.data.account

  return (
    <main className="flex flex-col gap-6">
      <section className="flex flex-col gap-1">
        <h2 className="text-xl font-medium">Campaigns</h2>
        <p className="max-w-prose text-sm text-muted-foreground">
          What Amazfy last brought in from Google Ads. An enabled ad only runs if its ad group and campaign are enabled
          too. To change any of this, use Google Ads, then bring the campaigns in again.
        </p>
      </section>

      {campaigns.isError ? (
        <FormMessage error>{errorMessage(campaigns.error)}</FormMessage>
      ) : !list ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : list.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No campaigns yet. Bring them in on the{' '}
          <Link to="/ads" className="underline underline-offset-4">
            Ads page
          </Link>
          .
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {list.map((campaign) => (
            <CampaignRow key={campaign.id} campaign={campaign} accountId={accountId} chosen={chosen} />
          ))}
        </ul>
      )}
    </main>
  )
}

function CampaignRow({
  campaign,
  accountId,
  chosen,
}: {
  campaign: Campaign
  accountId: string
  chosen?: GoogleAdsAccount
}) {
  const [open, setOpen] = useState(false)
  // Sample data has no Google Ads account, and without a chosen account there's nothing to compare with
  const cameFrom = campaign.channelAccountId
  const fromEarlierAccount = chosen && cameFrom && cameFrom !== chosen.customerId
  const Chevron = open ? ChevronDownIcon : ChevronRightIcon

  return (
    <li className="rounded-lg border text-sm">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        className="flex w-full items-start gap-2 rounded-lg p-4 text-left hover:bg-muted/50"
      >
        <Chevron className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
        <span className="flex min-w-0 flex-col gap-1">
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-medium break-words">{campaign.name}</span>
            <Status status={campaign.status} />
          </span>
          <span className="text-muted-foreground">
            {budget(campaign)} · {plural(campaign.adGroupCount, 'ad group', 'ad groups')} ·{' '}
            {plural(campaign.adCount, 'ad', 'ads')}
          </span>
          {fromEarlierAccount && (
            <span className="text-muted-foreground">
              From an earlier Google Ads account ({accountNumber(cameFrom)}), so it no longer updates.
            </span>
          )}
        </span>
      </button>
      {open && <AdGroups accountId={accountId} campaignId={campaign.id} />}
    </li>
  )
}

function AdGroups({ accountId, campaignId }: { accountId: string; campaignId: string }) {
  const adGroups = useListCampaignAdGroups(accountId, campaignId)
  const list = adGroups.data?.data

  return (
    <div className="flex flex-col gap-4 border-t p-4 pl-10">
      {adGroups.isError ? (
        <FormMessage error>{errorMessage(adGroups.error)}</FormMessage>
      ) : !list ? (
        <p className="text-muted-foreground">Loading…</p>
      ) : list.length === 0 ? (
        <p className="text-muted-foreground">This campaign has no ad groups.</p>
      ) : (
        list.map((group) => (
          <section key={group.id} className="flex flex-col gap-2">
            <h3 className="flex flex-wrap items-center gap-2 font-medium">
              <span className="break-words">{group.name}</span>
              <Status status={group.status} />
            </h3>
            {group.ads.length === 0 ? (
              <p className="text-muted-foreground">This ad group has no ads.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {group.ads.map((ad) => (
                  <AdRow key={ad.id} ad={ad} />
                ))}
              </ul>
            )}
          </section>
        ))
      )}
    </div>
  )
}

function AdRow({ ad }: { ad: CampaignAd }) {
  return (
    <li className="flex flex-col gap-1 rounded-md bg-muted/40 p-3">
      <span className="flex flex-wrap items-center gap-2">
        {ad.finalUrl ? (
          <span className="font-mono text-xs break-all">{ad.finalUrl}</span>
        ) : (
          <span className="text-muted-foreground">No final URL (Ad {ad.externalId})</span>
        )}
        <Status status={ad.status} />
      </span>
      <span className="text-muted-foreground">
        {ad.productName ? (
          `Linked to product ${ad.productName}`
        ) : ad.brandName ? (
          `Linked to brand ${ad.brandName}`
        ) : (
          <>
            Not linked yet:{' '}
            <Link to="/ads" className="underline underline-offset-4">
              link it on the Ads page
            </Link>
          </>
        )}
      </span>
    </li>
  )
}

const STATUS_LABELS: Record<ChannelStatus, string> = {
  [ChannelStatus.ENABLED]: 'Enabled',
  [ChannelStatus.PAUSED]: 'Paused',
  [ChannelStatus.REMOVED]: 'Removed',
}

function Status({ status }: { status: ChannelStatus }) {
  return (
    <span
      className={cn(
        'rounded-full border px-2 py-0.5 text-xs',
        status === ChannelStatus.ENABLED ? 'text-foreground' : 'bg-muted text-muted-foreground',
      )}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}

function budget(campaign: Campaign) {
  return campaign.dailyBudget !== undefined && campaign.budgetCurrency
    ? `${money(campaign.dailyBudget, campaign.budgetCurrency)} a day`
    : 'No daily budget'
}

function plural(n: number, one: string, many: string) {
  return `${count(n)} ${n === 1 ? one : many}`
}

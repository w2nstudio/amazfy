import { useSearchParams } from 'react-router'
import { regionName } from '@/account/amazon'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import { useResendVerification } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { Role } from '@/api/generated/model'
import { useGetSystemInfo } from '@/api/generated/system/system'
import { FormMessage } from '@/components/AuthCard'
import { SampleDataPanel } from '@/components/SampleDataPanel'
import { Button } from '@/components/ui/button'
import { DateRangePicker } from '@/reports/DateRangePicker'
import { rangeFromSearch, rangeToSearch } from '@/reports/dateRange'
import { DrillDownReports } from '@/reports/DrillDownReports'
import { ReportSummary } from '@/reports/ReportSummary'

// The dashboard (inside AppLayout): the current seller account's totals for the chosen days, and the same numbers
// per day and per brand, product, campaign, ad group or ad (drill-down).
export function HomePage() {
  const me = useGetMe()
  const systemInfo = useGetSystemInfo()
  const user = me.data?.data
  const { current } = useCurrentAccount()
  const region = current && regionName(current.account.region)
  const [searchParams, setSearchParams] = useSearchParams()
  const range = rangeFromSearch(searchParams)

  return (
    <main className="flex flex-1 flex-col gap-8">
      {/* Until the email is confirmed: a reminder with a way to get the link again */}
      {user && !user.emailVerified && <VerifyEmailBanner email={user.email} />}

      <section className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h2 className="text-xl font-medium">{current?.account.name}</h2>
          <p className="text-sm text-muted-foreground">
            Google ads and the Amazon sales they brought{region && ` · ${region}`}
          </p>
        </div>
        <DateRangePicker range={range} onChange={(r) => setSearchParams(rangeToSearch(r))} />
      </section>

      {current && <ReportSummary accountId={current.account.id} range={range} />}
      {/* key: switching accounts starts the drill-down from the top (the old path's ids belong to the other account) */}
      {current && <DrillDownReports key={current.account.id} accountId={current.account.id} range={range} />}

      {/* import.meta.env.DEV: true only under `npm run dev`; a production build leaves this out entirely */}
      {import.meta.env.DEV && current?.role === Role.OWNER && <SampleDataPanel accountId={current.account.id} />}

      <p className="mt-auto text-xs text-muted-foreground">
        {systemInfo.isSuccess && `Backend: ${systemInfo.data.data.name} ${systemInfo.data.data.version}`}
      </p>
    </main>
  )
}

function VerifyEmailBanner({ email }: { email: string }) {
  const resend = useResendVerification()
  return (
    <section className="flex flex-col gap-2 rounded-lg border p-4 text-sm">
      <p>
        Please confirm your email address: we sent a link to <strong>{email}</strong>.
      </p>
      <div className="flex items-center gap-3">
        <Button variant="outline" size="sm" onClick={() => resend.mutate()} disabled={resend.isPending}>
          Send the email again
        </Button>
        <FormMessage>{resend.isSuccess && 'Sent. The new link replaces the old one.'}</FormMessage>
        <FormMessage error>{errorMessage(resend.error)}</FormMessage>
      </div>
    </section>
  )
}

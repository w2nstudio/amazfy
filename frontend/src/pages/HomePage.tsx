import { Link } from 'react-router'
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

// Placeholder home page for logged-in users (inside AppLayout). The dashboard replaces it in M3.
export function HomePage() {
  const me = useGetMe()
  const systemInfo = useGetSystemInfo()
  const user = me.data?.data
  const { current } = useCurrentAccount()
  const region = current && regionName(current.account.region)

  return (
    <main className="flex flex-1 flex-col gap-8">
      {/* Until the email is confirmed: a reminder with a way to get the link again */}
      {user && !user.emailVerified && <VerifyEmailBanner email={user.email} />}

      <section className="flex flex-col gap-2">
        <h2 className="text-xl font-medium">{current?.account.name}</h2>
        <p className="text-sm text-muted-foreground">
          You are the {current?.role.toLowerCase()} of this Amazon seller account
          {region && ` in ${region}`}. Start by adding its{' '}
          <Link to="/brands" className="underline underline-offset-4">
            brands
          </Link>
          ; products and the dashboard follow.
        </p>
      </section>

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

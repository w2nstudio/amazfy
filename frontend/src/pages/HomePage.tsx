import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { AccountSwitcher } from '@/account/AccountSwitcher'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage, setAccessToken } from '@/api/client'
import { useLogout, useResendVerification } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { useGetSystemInfo } from '@/api/generated/system/system'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Placeholder home page for logged-in users (behind RequireAuth). The dashboard replaces it in M3.
export function HomePage() {
  const me = useGetMe()
  const systemInfo = useGetSystemInfo()
  const logout = useLogout()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  function logOut() {
    // Ends the session on the server (refresh cookie revoked and cleared), then forgets everything here
    logout.mutate(undefined, {
      onSettled: () => {
        setAccessToken(null)
        queryClient.clear()
        navigate('/login', { replace: true })
      },
    })
  }

  const user = me.data?.data
  const { current } = useCurrentAccount()

  // Until the email is confirmed: a reminder with a way to get the link again
  const verificationBanner = user && !user.emailVerified && <VerifyEmailBanner email={user.email} />

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col gap-8 p-6">
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-2xl font-semibold tracking-tight">Amazfy</h1>
          <AccountSwitcher />
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span className="text-muted-foreground">{user?.email}</span>
          <Button variant="outline" size="sm" onClick={logOut} disabled={logout.isPending}>
            Log out
          </Button>
        </div>
      </header>

      {verificationBanner}

      <section className="flex flex-col gap-2">
        <h2 className="text-xl font-medium">{current?.account.name}</h2>
        <p className="text-sm text-muted-foreground">
          You are the {current?.role.toLowerCase()} of this account. Brands, products and the dashboard arrive here in
          the next milestones.
        </p>
      </section>

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

import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { setAccessToken } from '@/api/client'
import { useLogout } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { useGetSystemInfo } from '@/api/generated/system/system'
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

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col gap-8 p-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">Amazfy</h1>
        <div className="flex items-center gap-3 text-sm">
          <span className="text-muted-foreground">{user?.email}</span>
          <Button variant="outline" size="sm" onClick={logOut} disabled={logout.isPending}>
            Log out
          </Button>
        </div>
      </header>

      <section className="flex flex-col gap-2">
        <h2 className="font-medium">Your accounts</h2>
        <ul className="flex flex-col gap-1 text-sm">
          {user?.accounts.map(({ account, role }) => (
            <li key={account.id}>
              {account.name} <span className="text-muted-foreground">({role.toLowerCase()})</span>
            </li>
          ))}
        </ul>
      </section>

      <p className="mt-auto text-xs text-muted-foreground">
        {systemInfo.isSuccess && `Backend: ${systemInfo.data.data.name} ${systemInfo.data.data.version}`}
      </p>
    </main>
  )
}

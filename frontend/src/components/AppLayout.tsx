import { useQueryClient } from '@tanstack/react-query'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { AccountSwitcher } from '@/account/AccountSwitcher'
import { setAccessToken } from '@/api/client'
import { useLogout } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// The frame around every logged-in page: header with the account switcher, the page links and logout.
// Pages render below it (<Outlet />); they are registered as children of this layout in src/router.tsx.
export function AppLayout() {
  const me = useGetMe()
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

  return (
    <div className="mx-auto flex min-h-svh max-w-4xl flex-col gap-8 p-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-4">
          <h1 className="text-2xl font-semibold tracking-tight">Amazfy</h1>
          <AccountSwitcher />
          <nav className="flex gap-1 text-sm">
            <PageLink to="/">Home</PageLink>
            <PageLink to="/brands">Brands</PageLink>
            <PageLink to="/products">Products</PageLink>
            <PageLink to="/import">Import</PageLink>
          </nav>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span className="text-muted-foreground">{me.data?.data.email}</span>
          <Button variant="outline" size="sm" onClick={logOut} disabled={logout.isPending}>
            Log out
          </Button>
        </div>
      </header>
      <Outlet />
    </div>
  )
}

function PageLink({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        cn(
          'rounded-md px-3 py-1.5 hover:bg-muted',
          isActive ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground',
        )
      }
    >
      {children}
    </NavLink>
  )
}

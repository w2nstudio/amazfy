import { Navigate, Outlet, useLocation } from 'react-router'
import { ApiError } from '@/api/client'
import { useGetMe } from '@/api/generated/me/me'

// Wraps every page that needs a logged-in user (see router.tsx). Not logged in → the login page, which sends the
// user back here afterwards (?next=...).
export function RequireAuth() {
  const me = useGetMe()
  const location = useLocation()

  if (me.isPending) return <CenteredMessage>Loading…</CenteredMessage>
  if (me.error instanceof ApiError && me.error.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (me.isError) return <CenteredMessage>Can't reach the Amazfy server. Please try again in a moment.</CenteredMessage>
  return <Outlet />
}

export function CenteredMessage({ children }: { children: React.ReactNode }) {
  return <main className="flex min-h-svh items-center justify-center p-6 text-muted-foreground">{children}</main>
}

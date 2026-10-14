import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError, setAccessToken } from '@/api/client'
import { useLogin } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// Messages for ?error=... set by the backend's redirects (e.g. a failed Google sign-in)
const redirectErrors: Record<string, string> = {
  google: 'Google sign-in failed. Please try again.',
}

/** Only follow ?next= to our own pages, never to another site ("open redirect"). */
function safeNext(next: string | null) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function LoginPage() {
  const [searchParams] = useSearchParams()
  const next = safeNext(searchParams.get('next'))
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const me = useGetMe()
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // Already logged in (e.g. opened /login in a new tab): go straight on
  if (me.isSuccess) return <Navigate to={next} replace />

  function submit(event: React.FormEvent) {
    event.preventDefault()
    login.mutate(
      { data: { email, password } },
      {
        onSuccess: async (response) => {
          setAccessToken(response.data.accessToken)
          await queryClient.resetQueries() // forget everything cached while logged out
          navigate(next, { replace: true })
        },
      },
    )
  }

  const redirectError = redirectErrors[searchParams.get('error') ?? '']
  const errorMessage =
    login.error instanceof ApiError
      ? login.error.message
      : login.error
        ? 'Something went wrong. Please try again.'
        : redirectError

  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl">Log in to Amazfy</CardTitle>
          <CardDescription>External traffic, measured in Amazon sales.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {errorMessage && (
              <p role="alert" className="text-sm text-destructive">
                {errorMessage}
              </p>
            )}
            <Button type="submit" disabled={login.isPending}>
              {login.isPending ? 'Logging in…' : 'Log in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}

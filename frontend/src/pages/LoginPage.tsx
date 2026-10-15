import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { errorMessage, setAccessToken } from '@/api/client'
import { useLogin } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

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

  const message = errorMessage(login.error) ?? redirectErrors[searchParams.get('error') ?? '']

  return (
    <AuthCard
      title="Log in to Amazfy"
      description="External traffic, measured in Amazon sales."
      footer={
        <>
          <Link to="/forgot-password" className="underline-offset-4 hover:underline">
            Forgot your password?
          </Link>
          <span>
            New to Amazfy?{' '}
            <Link to="/signup" className="text-foreground underline-offset-4 hover:underline">
              Create an account
            </Link>
          </span>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <FormMessage error>{message}</FormMessage>
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? 'Logging in…' : 'Log in'}
        </Button>
      </form>
    </AuthCard>
  )
}

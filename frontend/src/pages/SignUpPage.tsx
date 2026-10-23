import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { errorMessage, setAccessToken } from '@/api/client'
import { useRegister } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { GoogleButton } from '@/components/GoogleButton'
import { Button } from '@/components/ui/button'

// Sign-up: creates the user (their login), their first Amazfy account (= one Amazon seller account) and logs them in.
// The backend also sends a verification email; the home page reminds the user until they click it.
export function SignUpPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const me = useGetMe()
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [accountName, setAccountName] = useState('')

  if (me.isSuccess) return <Navigate to="/" replace />

  function submit(event: React.FormEvent) {
    event.preventDefault()
    register.mutate(
      { data: { email, password, accountName } },
      {
        onSuccess: async (response) => {
          setAccessToken(response.data.accessToken)
          await queryClient.resetQueries()
          navigate('/', { replace: true })
        },
      },
    )
  }

  return (
    <AuthCard
      title="Sign up for Amazfy"
      description="Your login, plus your first Amazon seller account. You can add more Amazon seller accounts later."
      footer={
        <span>
          Already signed up?{' '}
          <Link to="/login" className="text-foreground underline-offset-4 hover:underline">
            Log in
          </Link>
        </span>
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
          error={register.error}
        />
        <Field
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 10 characters. A few words in a row work well."
          required
          minLength={10}
          maxLength={128}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={register.error}
        />
        <Field
          id="accountName"
          label="Name of your Amazon seller account"
          hint='The name you know it by, e.g. "AquaLife Inc. – North America". Brands are added later.'
          required
          maxLength={100}
          value={accountName}
          onChange={(e) => setAccountName(e.target.value)}
          error={register.error}
        />
        <FormMessage error>{errorMessage(register.error)}</FormMessage>
        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? 'Signing you up…' : 'Sign up'}
        </Button>
      </form>
      <div className="mt-4">
        <GoogleButton />
      </div>
    </AuthCard>
  )
}

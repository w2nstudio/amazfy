import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { errorMessage, setAccessToken } from '@/api/client'
import { useRegister } from '@/api/generated/auth/auth'
import { useGetMe } from '@/api/generated/me/me'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { GoogleButton } from '@/components/GoogleButton'
import { Button } from '@/components/ui/button'

// Sign-up: creates the user, their first account (the business) and logs them in right away.
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
      title="Create your Amazfy account"
      description="One account per Amazon seller account. You can add more later."
      footer={
        <span>
          Already have an account?{' '}
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
          label="Business name"
          hint='How your Amazon business is called, e.g. "AquaLife US".'
          required
          maxLength={100}
          value={accountName}
          onChange={(e) => setAccountName(e.target.value)}
          error={register.error}
        />
        <FormMessage error>{errorMessage(register.error)}</FormMessage>
        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? 'Creating your account…' : 'Create account'}
        </Button>
      </form>
      <div className="mt-4">
        <GoogleButton />
      </div>
    </AuthCard>
  )
}

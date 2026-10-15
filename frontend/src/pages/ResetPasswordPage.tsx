import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage, setAccessToken } from '@/api/client'
import { useResetPassword } from '@/api/generated/auth/auth'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Opened from the link in the reset email (/reset-password?token=...): choose a new password.
// The backend then logs the user out everywhere, so they log in again with the new password.
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const queryClient = useQueryClient()
  const reset = useResetPassword()
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const mismatch = repeat.length > 0 && password !== repeat

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (mismatch) return
    reset.mutate(
      { data: { token, newPassword: password } },
      {
        onSuccess: () => {
          // All sessions were ended on the server, including this browser's
          setAccessToken(null)
          queryClient.clear()
        },
      },
    )
  }

  if (reset.isSuccess) {
    return (
      <AuthCard title="Password changed">
        <div className="flex flex-col gap-4">
          <FormMessage>Your new password is set. For your safety you've been logged out everywhere.</FormMessage>
          <Button asChild>
            <Link to="/login">Log in</Link>
          </Button>
        </div>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Choose a new password"
      footer={
        <Link to="/forgot-password" className="underline-offset-4 hover:underline">
          Need a new link?
        </Link>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field
          id="newPassword"
          label="New password"
          type="password"
          autoComplete="new-password"
          hint="At least 10 characters."
          required
          minLength={10}
          maxLength={128}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={reset.error}
        />
        <Field
          id="repeatPassword"
          label="Repeat new password"
          type="password"
          autoComplete="new-password"
          required
          value={repeat}
          onChange={(e) => setRepeat(e.target.value)}
        />
        <FormMessage error>{mismatch ? "The passwords don't match." : errorMessage(reset.error)}</FormMessage>
        <Button type="submit" disabled={reset.isPending || !token}>
          {reset.isPending ? 'Saving…' : 'Set new password'}
        </Button>
      </form>
    </AuthCard>
  )
}

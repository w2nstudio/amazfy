import { useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '@/api/client'
import { useForgotPassword } from '@/api/generated/auth/auth'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// "Forgot your password?": asks the backend to email a reset link. The answer is the same whether or not the email
// has an account, so this page can't be used to find out who uses Amazfy.
export function ForgotPasswordPage() {
  const forgot = useForgotPassword()
  const [email, setEmail] = useState('')

  function submit(event: React.FormEvent) {
    event.preventDefault()
    forgot.mutate({ data: { email } })
  }

  return (
    <AuthCard
      title="Reset your password"
      description="Enter your email and we'll send you a link to choose a new password."
      footer={
        <Link to="/login" className="text-foreground underline-offset-4 hover:underline">
          Back to log in
        </Link>
      }
    >
      {forgot.isSuccess ? (
        <FormMessage>
          If an Amazfy login exists for {email}, a reset link is on its way. It works once and expires in 1 hour.
        </FormMessage>
      ) : (
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
          <FormMessage error>{errorMessage(forgot.error)}</FormMessage>
          <Button type="submit" disabled={forgot.isPending}>
            {forgot.isPending ? 'Sending…' : 'Send reset link'}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}

import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '@/api/client'
import { useVerifyEmail } from '@/api/generated/auth/auth'
import { getGetMeQueryKey } from '@/api/generated/me/me'
import { AuthCard, FormMessage } from '@/components/AuthCard'

// Opened from the link in the verification email (/verify-email?token=...). Confirms the address right away.
// Works whether or not the user is logged in in this browser.
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const queryClient = useQueryClient()
  const verify = useVerifyEmail()
  const sent = useRef(false)

  useEffect(() => {
    // Only once: the token works a single time, and React runs effects twice in development (StrictMode)
    if (!token || sent.current) return
    sent.current = true
    verify.mutate(
      { data: { token } },
      { onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetMeQueryKey() }) },
    )
  }, [token, verify, queryClient])

  return (
    <AuthCard
      title="Confirm your email"
      footer={
        <Link to="/" className="text-foreground underline-offset-4 hover:underline">
          Go to Amazfy
        </Link>
      }
    >
      {!token && <FormMessage error>This link is incomplete. Please open the link from the email again.</FormMessage>}
      {verify.isPending && <FormMessage>Confirming your email…</FormMessage>}
      {verify.isSuccess && <FormMessage>Your email is confirmed. Thank you!</FormMessage>}
      {verify.isError && (
        <FormMessage error>
          {errorMessage(verify.error)} You can ask for a new one on the Amazfy home page after logging in.
        </FormMessage>
      )}
    </AuthCard>
  )
}

import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { setCurrentAccountId } from '@/account/currentAccount'
import { RegionField } from '@/account/RegionField'
import { errorMessage } from '@/api/client'
import { useCreateAccount } from '@/api/generated/accounts/accounts'
import { getGetMeQueryKey } from '@/api/generated/me/me'
import type { Region } from '@/api/generated/model'
import { AuthCard, Field, FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'

// Creates an account (one per Amazon seller account); the user becomes its owner and switches to it.
// - /onboarding: the user has no account yet, e.g. right after their first "Sign in with Google"
// - /accounts/new: "Add Amazon seller account" from the switcher, e.g. for a second one in Europe
export function CreateAccountPage({ firstAccount }: { firstAccount: boolean }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const create = useCreateAccount()
  const [name, setName] = useState('')
  const [region, setRegion] = useState<Region>()
  const [regionMissing, setRegionMissing] = useState(false)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!region) return setRegionMissing(true)
    create.mutate(
      { data: { name, region } },
      {
        onSuccess: async (response) => {
          setCurrentAccountId(response.data.id)
          await queryClient.invalidateQueries({ queryKey: getGetMeQueryKey() })
          navigate('/', { replace: true })
        },
      },
    )
  }

  return (
    <AuthCard
      title={firstAccount ? 'Welcome to Amazfy' : 'Add an Amazon seller account'}
      description={
        firstAccount
          ? 'Start with one Amazon seller account. You add its brands and products next, and more Amazon seller accounts any time.'
          : 'Each Amazon seller account is kept separate, e.g. one for North America and one for Europe.'
      }
      footer={
        !firstAccount && (
          <Link to="/" className="underline-offset-4 hover:underline">
            Cancel
          </Link>
        )
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field
          id="name"
          label="Name of this Amazon seller account"
          hint='The name you know it by, e.g. "AquaLife Inc. – North America". Brands are added separately.'
          required
          maxLength={100}
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={create.error}
        />
        <RegionField value={region} onChange={setRegion} missing={regionMissing} />
        <FormMessage error>{errorMessage(create.error)}</FormMessage>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : firstAccount ? 'Continue' : 'Add Amazon seller account'}
        </Button>
      </form>
    </AuthCard>
  )
}

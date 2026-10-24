import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import {
  getListBrandsQueryKey,
  useCreateBrand,
  useDeleteBrand,
  useListBrands,
  useRenameBrand,
} from '@/api/generated/brands/brands'
import { Role, type Brand } from '@/api/generated/model'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

// The brands of the current Amazon seller account (e.g. AquaLife, ClearTank). Products are added under a brand.
// Owners and editors can add, rename and remove brands; viewers only see the list (the backend enforces the same).
export function BrandsPage() {
  const { current } = useCurrentAccount()
  if (!current) return null
  // key: switching accounts starts the page fresh (no half-typed name carried over to the other account)
  return <Brands key={current.account.id} accountId={current.account.id} canEdit={current.role !== Role.VIEWER} />
}

function Brands({ accountId, canEdit }: { accountId: string; canEdit: boolean }) {
  const brands = useListBrands(accountId)
  const list = brands.data?.data ?? []

  return (
    <main className="flex flex-col gap-6">
      <section className="flex flex-col gap-1">
        <h2 className="text-xl font-medium">Brands</h2>
        <p className="text-sm text-muted-foreground">
          The brands you sell under in this Amazon seller account. Each product belongs to one brand.
        </p>
      </section>

      {canEdit && <AddBrandForm accountId={accountId} />}

      {brands.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
      <FormMessage error>{errorMessage(brands.error)}</FormMessage>
      {brands.isSuccess && list.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No brands yet.{canEdit && ' Add the first one above, e.g. the brand name shown on your Amazon listings.'}
        </p>
      )}

      {list.length > 0 && (
        <ul className="divide-y rounded-lg border">
          {list.map((brand) => (
            <BrandRow key={brand.id} accountId={accountId} brand={brand} canEdit={canEdit} />
          ))}
        </ul>
      )}
    </main>
  )
}

/** After any change, the list is loaded again (it is sorted by the server, and product counts may change). */
function useRefreshBrands(accountId: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: getListBrandsQueryKey(accountId) })
}

function AddBrandForm({ accountId }: { accountId: string }) {
  const create = useCreateBrand()
  const refresh = useRefreshBrands(accountId)
  const [name, setName] = useState('')

  function submit(event: React.FormEvent) {
    event.preventDefault()
    create.mutate(
      { accountId, data: { name } },
      {
        onSuccess: async () => {
          setName('')
          await refresh()
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <div className="flex gap-2">
        <Input
          aria-label="New brand name"
          placeholder="Brand name, e.g. AquaLife"
          required
          maxLength={100}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Adding…' : 'Add brand'}
        </Button>
      </div>
      <FormMessage error>{errorMessage(create.error)}</FormMessage>
    </form>
  )
}

/** One brand: its name and product count, with rename and remove for owners and editors. */
function BrandRow({ accountId, brand, canEdit }: { accountId: string; brand: Brand; canEdit: boolean }) {
  const rename = useRenameBrand()
  const remove = useDeleteBrand()
  const refresh = useRefreshBrands(accountId)
  const [editing, setEditing] = useState(false)
  const [confirmingRemove, setConfirmingRemove] = useState(false)
  const [name, setName] = useState(brand.name)

  function saveName(event: React.FormEvent) {
    event.preventDefault()
    rename.mutate(
      { accountId, brandId: brand.id, data: { name } },
      {
        onSuccess: async () => {
          setEditing(false)
          await refresh()
        },
      },
    )
  }

  function cancelEditing() {
    setEditing(false)
    setName(brand.name)
    rename.reset()
  }

  const products = brand.productCount === 1 ? '1 product' : `${brand.productCount || 'No'} products`

  return (
    <li className="flex flex-col gap-2 p-4">
      {editing ? (
        <form onSubmit={saveName} className="flex gap-2">
          <Input
            aria-label="Brand name"
            required
            maxLength={100}
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Button type="submit" size="sm" disabled={rename.isPending}>
            Save
          </Button>
          <Button type="button" size="sm" variant="ghost" onClick={cancelEditing}>
            Cancel
          </Button>
        </form>
      ) : (
        <div className="flex items-center justify-between gap-4">
          <div className="flex flex-col">
            <span className="font-medium">{brand.name}</span>
            <Link
              to={`/products?brand=${brand.id}`}
              className="text-sm text-muted-foreground underline-offset-4 hover:underline"
            >
              {products}
            </Link>
          </div>
          {canEdit && !confirmingRemove && (
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
                Rename
              </Button>
              {/* A brand with products can't be removed (the server answers 409), so the button explains instead */}
              <Button
                size="sm"
                variant="outline"
                disabled={brand.productCount > 0}
                title={brand.productCount > 0 ? 'Move or remove its products first' : undefined}
                onClick={() => setConfirmingRemove(true)}
              >
                Remove
              </Button>
            </div>
          )}
          {confirmingRemove && (
            <div className="flex items-center gap-2 text-sm">
              <span>Remove {brand.name}?</span>
              <Button
                size="sm"
                variant="destructive"
                disabled={remove.isPending}
                onClick={() => remove.mutate({ accountId, brandId: brand.id }, { onSuccess: refresh })}
              >
                Remove
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setConfirmingRemove(false)
                  remove.reset()
                }}
              >
                Cancel
              </Button>
            </div>
          )}
        </div>
      )}
      <FormMessage error>{errorMessage(rename.error) ?? errorMessage(remove.error)}</FormMessage>
    </li>
  )
}

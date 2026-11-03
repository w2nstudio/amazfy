import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { AMAZON_SITES } from '@/account/amazon'
import { errorMessage } from '@/api/client'
import { useAutoLinkAds } from '@/api/generated/ads/ads'
import { useCreateBrand } from '@/api/generated/brands/brands'
import type { Brand, Marketplace } from '@/api/generated/model'
import { useCreateProduct } from '@/api/generated/products/products'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

// "Add this product" on an unlinked ad that points to a product page: the ASIN and marketplace come from the ad's final
// URL, the seller adds a name and a brand (an existing one or a new one). Then Link automatically runs, so every ad
// pointing to that page links at once, not only this one.

const NEW_BRAND = 'new'

export function AddProductFromAd({
  accountId,
  adId,
  asin,
  marketplace,
  brands,
  onAdded,
  onCancel,
}: {
  accountId: string
  adId: string
  asin: string
  marketplace: Marketplace
  brands: Brand[]
  /** Told what happened, since this ad's row goes away once it's linked. */
  onAdded: (message: string) => void
  onCancel: () => void
}) {
  const createBrand = useCreateBrand()
  const createProduct = useCreateProduct()
  const autoLink = useAutoLinkAds()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [brandChoice, setBrandChoice] = useState(brands[0]?.id ?? NEW_BRAND)
  const [newBrandName, setNewBrandName] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const site = AMAZON_SITES[marketplace]

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setSaving(true)
    try {
      // One step after the other: a brand added before a failing step stays, and shows on the Brands page
      const brandId =
        brandChoice === NEW_BRAND
          ? (await createBrand.mutateAsync({ accountId, data: { name: newBrandName } })).data.id
          : brandChoice
      await createProduct.mutateAsync({ accountId, data: { brandId, asin, marketplace, name } })
      const linking = (await autoLink.mutateAsync({ accountId })).data
      const product = `${name} (${asin} on ${site})`
      // Normally at least this ad; none only if someone linked it by hand meanwhile
      const others = linking.linked - 1
      onAdded(
        linking.linked === 0
          ? `Added ${product}.`
          : `Added ${product} and linked ${others > 0 ? `this ad and ${others} more` : 'this ad'}.`,
      )
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
      // Products, brands, this list and the dashboard's rows may all have changed, also after a step that failed
      await queryClient.invalidateQueries()
    }
  }

  return (
    <form
      onSubmit={submit}
      className="flex flex-col gap-4 rounded-md border bg-muted/30 p-4"
      aria-label={`Add product ${asin}`}
    >
      <p>
        Add <span className="font-mono">{asin}</span> on {site} to your products, then link the ads pointing to it.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor={`name-${adId}`}>Name</Label>
          <Input
            id={`name-${adId}`}
            placeholder="Aquarium filter 40L"
            required
            maxLength={200}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            A short name you recognise; it doesn&apos;t have to match Amazon.
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor={`brand-${adId}`}>Brand</Label>
          <Select value={brandChoice} onValueChange={setBrandChoice}>
            <SelectTrigger id={`brand-${adId}`} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {brands.map((brand) => (
                <SelectItem key={brand.id} value={brand.id}>
                  {brand.name}
                </SelectItem>
              ))}
              <SelectItem value={NEW_BRAND}>New brand…</SelectItem>
            </SelectContent>
          </Select>
          {brandChoice === NEW_BRAND && (
            <Input
              aria-label="New brand's name"
              placeholder="AquaLife"
              required
              maxLength={100}
              value={newBrandName}
              onChange={(e) => setNewBrandName(e.target.value)}
            />
          )}
        </div>
      </div>
      <FormMessage error>{errorMessage(error)}</FormMessage>
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={saving}>
          {saving ? 'Adding…' : 'Add product and link'}
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { AMAZON_SITES } from '@/account/amazon'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import { useAutoLinkAds, useLinkAd, useListUnlinkedAds } from '@/api/generated/ads/ads'
import { useListBrands } from '@/api/generated/brands/brands'
import { AdLinkKind, Role, type Brand, type Product, type UnlinkedAd } from '@/api/generated/model'
import { useListProducts } from '@/api/generated/products/products'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

// The current seller account's Google ads linked to no product or brand (requirement A7): the ads behind the report
// row "Not linked to a product or brand". Product pages of known products link automatically; for anything else the
// seller chooses. Viewers only see the list.

export function AdsPage() {
  const { current } = useCurrentAccount()
  if (!current) return null
  // key: switching accounts starts fresh (a choice made for one account's ad is never carried over)
  return <Ads key={current.account.id} accountId={current.account.id} canEdit={current.role !== Role.VIEWER} />
}

function Ads({ accountId, canEdit }: { accountId: string; canEdit: boolean }) {
  const ads = useListUnlinkedAds(accountId)
  const brands = useListBrands(accountId)
  const products = useListProducts(accountId)
  const autoLink = useAutoLinkAds()
  const queryClient = useQueryClient()
  const list = ads.data?.data
  const outcome = autoLink.data?.data
  const productList = products.data?.data ?? []
  const brandList = brands.data?.data ?? []

  function linkAutomatically() {
    // Everything may change: this list and the dashboard's rows
    autoLink.mutate({ accountId }, { onSuccess: () => queryClient.invalidateQueries() })
  }

  return (
    <main className="flex flex-col gap-6">
      <section className="flex flex-col gap-1">
        <h2 className="text-xl font-medium">Ads to link</h2>
        <p className="max-w-prose text-sm text-muted-foreground">
          These ads aren't linked to a product or brand yet, so their spend shows as "Not linked to a product or brand"
          in your reports. Once linked, it moves under the product or brand straight away.
        </p>
      </section>

      {ads.isError ? (
        <FormMessage error>{errorMessage(ads.error)}</FormMessage>
      ) : !list ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <>
          {canEdit && (list.length > 0 || outcome) && (
            <section className="flex flex-wrap items-center gap-3">
              {list.length > 0 && (
                <Button size="sm" variant="outline" onClick={linkAutomatically} disabled={autoLink.isPending}>
                  {autoLink.isPending ? 'Linking…' : 'Link automatically'}
                </Button>
              )}
              <FormMessage>
                {outcome &&
                  (outcome.linked > 0
                    ? `Linked ${outcome.linked} ${outcome.linked === 1 ? 'ad' : 'ads'}; ${outcome.stillUnlinked} left.`
                    : 'No ad could be linked automatically: none points to the page of a product you have added.')}
              </FormMessage>
              <FormMessage error>{errorMessage(autoLink.error)}</FormMessage>
            </section>
          )}

          {list.length === 0 ? (
            <p className="text-sm text-muted-foreground">No ads are waiting to be linked.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {list.map((ad) => (
                <AdRow
                  key={ad.id}
                  ad={ad}
                  accountId={accountId}
                  canEdit={canEdit}
                  products={productList}
                  brands={brandList}
                />
              ))}
            </ul>
          )}
          {!canEdit && list.length > 0 && (
            <p className="text-sm text-muted-foreground">
              Only owners and editors of this seller account can link ads.
            </p>
          )}
        </>
      )}
    </main>
  )
}

function AdRow({
  ad,
  accountId,
  canEdit,
  products,
  brands,
}: {
  ad: UnlinkedAd
  accountId: string
  canEdit: boolean
  products: Product[]
  brands: Brand[]
}) {
  const [choice, setChoice] = useState('')
  const link = useLinkAd()
  const queryClient = useQueryClient()

  function save() {
    const [kind, id] = choice.split(':')
    link.mutate(
      { accountId, adId: ad.id, data: kind === 'product' ? { productId: id } : { brandId: id } },
      { onSuccess: () => queryClient.invalidateQueries() }, // this list and the dashboard's rows
    )
  }

  return (
    <li className="flex flex-col gap-3 rounded-lg border p-4 text-sm">
      <div className="flex flex-col gap-1">
        <span className="font-medium">
          {ad.campaignName} › {ad.adGroupName}
        </span>
        <span className="font-mono text-xs break-all text-muted-foreground">{ad.finalUrl}</span>
        <Hint ad={ad} products={products} />
      </div>
      {canEdit && (
        <div className="flex flex-wrap items-center gap-2">
          <Select value={choice} onValueChange={setChoice}>
            <SelectTrigger className="w-72" aria-label={`Link ad ${ad.externalId} to`}>
              <SelectValue placeholder="Choose a product or brand" />
            </SelectTrigger>
            <SelectContent>
              {products.length > 0 && (
                <SelectGroup>
                  <SelectLabel>Products</SelectLabel>
                  {products.map((product) => (
                    <SelectItem key={product.id} value={`product:${product.id}`}>
                      {product.name} · {product.marketplace}
                    </SelectItem>
                  ))}
                </SelectGroup>
              )}
              {brands.length > 0 && (
                <SelectGroup>
                  <SelectLabel>Brands (brand-level spend)</SelectLabel>
                  {brands.map((brand) => (
                    <SelectItem key={brand.id} value={`brand:${brand.id}`}>
                      {brand.name}
                    </SelectItem>
                  ))}
                </SelectGroup>
              )}
            </SelectContent>
          </Select>
          <Button size="sm" onClick={save} disabled={!choice || link.isPending}>
            {link.isPending ? 'Linking…' : 'Link'}
          </Button>
          <FormMessage error>{errorMessage(link.error)}</FormMessage>
        </div>
      )}
    </li>
  )
}

/** What the ad's link is and what the seller can do about it. */
function Hint({ ad, products }: { ad: UnlinkedAd; products: Product[] }) {
  if (ad.linkKind === AdLinkKind.PRODUCT_PAGE) {
    const known = products.find((p) => p.asin === ad.asin && p.marketplace === ad.marketplace)
    const site = ad.marketplace && AMAZON_SITES[ad.marketplace]
    return known ? (
      <span>
        Points to your product {known.name}. <em>Link automatically</em> links it.
      </span>
    ) : (
      <span>
        Points to {ad.asin} on {site}, which isn't in your products yet.{' '}
        <Link to="/products" className="underline underline-offset-4">
          Add the product
        </Link>
        , then use <em>Link automatically</em>.
      </span>
    )
  }
  if (ad.linkKind === AdLinkKind.BRAND_STORE) {
    return <span>A Brand Store page has no single product: choose its brand, and its spend shows as brand-level.</span>
  }
  return <span>Amazfy can't read this link: choose the product or brand it advertises.</span>
}

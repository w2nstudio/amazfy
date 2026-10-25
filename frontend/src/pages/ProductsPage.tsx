import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AMAZON_SITES, regionMarketplaces, regionName } from '@/account/amazon'
import { useCurrentAccount } from '@/account/currentAccount'
import { errorMessage } from '@/api/client'
import { getListBrandsQueryKey, useListBrands } from '@/api/generated/brands/brands'
import { Role, type Brand, type Marketplace, type Product, type Region } from '@/api/generated/model'
import {
  getListProductsQueryKey,
  useCreateProduct,
  useDeleteProduct,
  useListProducts,
  useUpdateProduct,
} from '@/api/generated/products/products'
import { FormMessage } from '@/components/AuthCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '@/components/ui/select'

// The products of the current Amazon seller account: one per Amazon listing (ASIN + marketplace), under a brand.
// /products shows all, /products?brand=<id> one brand's (the Brands page links there). Viewers only see the list.

const ALL_BRANDS = 'all'

export function ProductsPage() {
  const { current } = useCurrentAccount()
  if (!current) return null
  // key: switching accounts starts the page fresh (no half-filled form carried over to the other account)
  return (
    <Products
      key={current.account.id}
      accountId={current.account.id}
      region={current.account.region}
      canEdit={current.role !== Role.VIEWER}
    />
  )
}

function Products({ accountId, region, canEdit }: { accountId: string; region: Region; canEdit: boolean }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const brands = useListBrands(accountId)
  const brandList = brands.data?.data ?? []
  // Only a brand of this account counts as a filter (an old link after switching accounts shows all products)
  const brandId = brandList.find((b) => b.id === searchParams.get('brand'))?.id
  const products = useListProducts(accountId, brandId ? { brandId } : undefined)
  const list = products.data?.data ?? []
  const brandName = (id: string) => brandList.find((b) => b.id === id)?.name ?? ''

  function filterBy(value: string) {
    setSearchParams(value === ALL_BRANDS ? {} : { brand: value })
  }

  return (
    <main className="flex flex-col gap-6">
      <section className="flex flex-col gap-1">
        <h2 className="text-xl font-medium">Products</h2>
        <p className="text-sm text-muted-foreground">
          The Amazon listings you advertise. Each one is an ASIN on one marketplace, under one of your brands.
        </p>
      </section>

      {brands.isSuccess && brandList.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Products belong to a brand, so start by{' '}
          <Link to="/brands" className="underline underline-offset-4">
            adding a brand
          </Link>
          .
        </p>
      ) : (
        <>
          {canEdit && brandList.length > 0 && (
            <AddProductForm
              accountId={accountId}
              region={region}
              brands={brandList}
              defaultBrandId={brandId ?? brandList[0].id}
            />
          )}

          <div className="flex items-center gap-2 text-sm">
            <span className="text-muted-foreground">Show</span>
            <Select value={brandId ?? ALL_BRANDS} onValueChange={filterBy}>
              <SelectTrigger className="w-56" aria-label="Show products of">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_BRANDS}>All brands</SelectItem>
                <SelectSeparator />
                {brandList.map((brand) => (
                  <SelectItem key={brand.id} value={brand.id}>
                    {brand.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {products.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
          <FormMessage error>{errorMessage(products.error ?? brands.error)}</FormMessage>
          {products.isSuccess && list.length === 0 && (
            <p className="text-sm text-muted-foreground">
              {brandId ? `${brandName(brandId)} has no products yet.` : 'No products yet.'}
            </p>
          )}

          {list.length > 0 && (
            <ul className="divide-y rounded-lg border">
              {list.map((product) => (
                <ProductRow
                  key={product.id}
                  accountId={accountId}
                  product={product}
                  brands={brandList}
                  brandName={brandName(product.brandId)}
                  canEdit={canEdit}
                />
              ))}
            </ul>
          )}
        </>
      )}
    </main>
  )
}

/** After any change: products (all filters) and brands (their product counts) are loaded again. */
function useRefreshCatalog(accountId: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: getListProductsQueryKey(accountId) }),
      queryClient.invalidateQueries({ queryKey: getListBrandsQueryKey(accountId) }),
    ])
}

function AddProductForm({
  accountId,
  region,
  brands,
  defaultBrandId,
}: {
  accountId: string
  region: Region
  brands: Brand[]
  defaultBrandId: string
}) {
  const create = useCreateProduct()
  const refresh = useRefreshCatalog(accountId)
  const [brandId, setBrandId] = useState(defaultBrandId)
  const [asin, setAsin] = useState('')
  // Only the marketplaces of the seller account's region (the backend refuses others)
  const marketplaces = regionMarketplaces(region)
  const [marketplace, setMarketplace] = useState<Marketplace>(marketplaces[0])
  const [name, setName] = useState('')

  function submit(event: React.FormEvent) {
    event.preventDefault()
    create.mutate(
      { accountId, data: { brandId, asin, marketplace, name } },
      {
        // Brand and marketplace stay as they are: the next product is often a sibling of this one
        onSuccess: async () => {
          setAsin('')
          setName('')
          await refresh()
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 rounded-lg border p-4">
      <h3 className="font-medium">Add a product</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="asin">ASIN</Label>
          <Input
            id="asin"
            placeholder="B0AAA11111"
            required
            maxLength={20}
            value={asin}
            onChange={(e) => setAsin(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            10 characters starting with B0, from the listing&apos;s address: amazon.com/dp/<strong>B0AAA11111</strong>
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="marketplace">Marketplace</Label>
          <Select value={marketplace} onValueChange={(value) => setMarketplace(value as Marketplace)}>
            <SelectTrigger id="marketplace" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {marketplaces.map((code) => (
                <SelectItem key={code} value={code}>
                  {code} · {AMAZON_SITES[code]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            The Amazon site where this listing sells{regionName(region) && `, in ${regionName(region)}`}.
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="name">Name</Label>
          <Input
            id="name"
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
          <Label htmlFor="brand">Brand</Label>
          <BrandSelect id="brand" brands={brands} value={brandId} onChange={setBrandId} />
        </div>
      </div>
      <FormMessage error>{errorMessage(create.error)}</FormMessage>
      <Button type="submit" className="self-start" disabled={create.isPending}>
        {create.isPending ? 'Adding…' : 'Add product'}
      </Button>
    </form>
  )
}

function BrandSelect({
  id,
  brands,
  value,
  onChange,
}: {
  id: string
  brands: Brand[]
  value: string
  onChange: (brandId: string) => void
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {brands.map((brand) => (
          <SelectItem key={brand.id} value={brand.id}>
            {brand.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** One product: name, ASIN, marketplace, brand and its Amazon link; edit (name, brand) and remove for editors. */
function ProductRow({
  accountId,
  product,
  brands,
  brandName,
  canEdit,
}: {
  accountId: string
  product: Product
  brands: Brand[]
  brandName: string
  canEdit: boolean
}) {
  const update = useUpdateProduct()
  const remove = useDeleteProduct()
  const refresh = useRefreshCatalog(accountId)
  const [editing, setEditing] = useState(false)
  const [confirmingRemove, setConfirmingRemove] = useState(false)
  const [name, setName] = useState(product.name)
  const [brandId, setBrandId] = useState(product.brandId)

  function save(event: React.FormEvent) {
    event.preventDefault()
    update.mutate(
      { accountId, productId: product.id, data: { name, brandId } },
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
    setName(product.name)
    setBrandId(product.brandId)
    update.reset()
  }

  return (
    <li className="flex flex-col gap-2 p-4">
      {editing ? (
        <form onSubmit={save} className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              aria-label="Product name"
              required
              maxLength={200}
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <BrandSelect id={`brand-${product.id}`} brands={brands} value={brandId} onChange={setBrandId} />
          </div>
          {/* ASIN and marketplace identify the listing, so they can't be edited */}
          <p className="text-xs text-muted-foreground">
            {product.asin} · {product.marketplace} can&apos;t change. For another listing, add a new product.
          </p>
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={update.isPending}>
              Save
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={cancelEditing}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-col">
            <span className="font-medium">{product.name}</span>
            <span className="text-sm text-muted-foreground">
              {brandName} · {product.asin} ·{' '}
              <a
                href={product.amazonUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="underline underline-offset-4"
              >
                {AMAZON_SITES[product.marketplace]}
              </a>
            </span>
          </div>
          {canEdit && !confirmingRemove && (
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
                Edit
              </Button>
              <Button size="sm" variant="outline" onClick={() => setConfirmingRemove(true)}>
                Remove
              </Button>
            </div>
          )}
          {confirmingRemove && (
            <div className="flex items-center gap-2 text-sm">
              <span>Remove {product.name}?</span>
              <Button
                size="sm"
                variant="destructive"
                disabled={remove.isPending}
                onClick={() => remove.mutate({ accountId, productId: product.id }, { onSuccess: refresh })}
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
      <FormMessage error>{errorMessage(update.error) ?? errorMessage(remove.error)}</FormMessage>
    </li>
  )
}

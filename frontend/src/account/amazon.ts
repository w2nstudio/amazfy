import { Marketplace, Region } from '@/api/generated/model'

// Amazon's geography as the pages show it. Mirrors the backend's common/amazon/Marketplace.kt.
// One Amazon seller account per region; it sells only on its region's marketplaces.

/** The Amazon site of each marketplace, as sellers know them (the codes are Amazon's own: UK, not GB). */
export const AMAZON_SITES: Record<Marketplace, string> = {
  US: 'amazon.com',
  CA: 'amazon.ca',
  UK: 'amazon.co.uk',
  DE: 'amazon.de',
  FR: 'amazon.fr',
  IT: 'amazon.it',
  ES: 'amazon.es',
}

export const REGIONS: Record<Region, { name: string; marketplaces: Marketplace[] }> = {
  NA: { name: 'North America', marketplaces: [Marketplace.US, Marketplace.CA] },
  EU: {
    name: 'Europe',
    marketplaces: [Marketplace.UK, Marketplace.DE, Marketplace.FR, Marketplace.IT, Marketplace.ES],
  },
}

/** E.g. "North America (amazon.com, amazon.ca)" */
export function regionLabel(region: Region) {
  const { name, marketplaces } = REGIONS[region]
  return `${name} (${marketplaces.map((m) => AMAZON_SITES[m]).join(', ')})`
}

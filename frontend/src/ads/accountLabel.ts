import type { GoogleAdsAccount } from '@/api/generated/model'

/** As Google Ads shows it: AquaLife (123-456-7890). */
export function accountLabel(account: GoogleAdsAccount) {
  const id = account.customerId.replace(/^(\d{3})(\d{3})(\d+)$/, '$1-$2-$3')
  return account.name ? `${account.name} (${id})` : id
}

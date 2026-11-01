import type { GoogleAdsAccount } from '@/api/generated/model'

/** As Google Ads writes it: 123-456-7890. */
export function accountNumber(customerId: string) {
  return customerId.replace(/^(\d{3})(\d{3})(\d+)$/, '$1-$2-$3')
}

/** As Google Ads shows it: AquaLife (123-456-7890). */
export function accountLabel(account: GoogleAdsAccount) {
  const id = accountNumber(account.customerId)
  return account.name ? `${account.name} (${id})` : id
}

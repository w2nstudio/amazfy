import { useSyncExternalStore } from 'react'
import { useGetMe } from '@/api/generated/me/me'

// Which of the user's accounts they are working in (the account switcher in the header changes it).
// Remembered in localStorage: it is only a preference, not a secret, and the backend checks access on every request.
// Pages from M2 on (brands, products, ...) call the API for this account: /api/v1/accounts/{id}/...

const STORAGE_KEY = 'amazfy.currentAccountId'
const listeners = new Set<() => void>()

function readStoredId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null // storage blocked (e.g. strict privacy settings): fall back to the first account
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  window.addEventListener('storage', listener) // switched in another tab
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', listener)
  }
}

export function setCurrentAccountId(id: string) {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // not remembered across reloads, but still switched below
  }
  listeners.forEach((listener) => listener())
}

/**
 * The user's accounts and the one they are working in: the remembered one if they still have access to it,
 * otherwise their first account. `current` is undefined only for a user without any account (→ onboarding).
 */
export function useCurrentAccount() {
  const me = useGetMe()
  const storedId = useSyncExternalStore(subscribe, readStoredId)
  const accounts = me.data?.data.accounts ?? []
  const current = accounts.find((a) => a.account.id === storedId) ?? accounts[0]
  return { current, accounts }
}

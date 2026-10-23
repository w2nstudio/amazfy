import { useNavigate } from 'react-router'
import { setCurrentAccountId, useCurrentAccount } from '@/account/currentAccount'
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '@/components/ui/select'

const ADD_ACCOUNT = 'add-account'

// Header dropdown: which Amazon seller account the user is working in, plus "Add Amazon seller account".
// Each entry is one Amazfy account = one Amazon seller account (brands live inside it, they are not listed here).
export function AccountSwitcher() {
  const { current, accounts } = useCurrentAccount()
  const navigate = useNavigate()
  if (!current) return null

  function choose(value: string) {
    if (value === ADD_ACCOUNT) navigate('/accounts/new')
    else setCurrentAccountId(value)
  }

  return (
    <Select value={current.account.id} onValueChange={choose}>
      <SelectTrigger className="w-56" aria-label="Current Amazon seller account">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {accounts.map(({ account }) => (
          <SelectItem key={account.id} value={account.id}>
            {account.name}
          </SelectItem>
        ))}
        <SelectSeparator />
        <SelectItem value={ADD_ACCOUNT}>+ Add Amazon seller account</SelectItem>
      </SelectContent>
    </Select>
  )
}

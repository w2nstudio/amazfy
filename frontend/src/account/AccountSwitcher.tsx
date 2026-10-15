import { useNavigate } from 'react-router'
import { setCurrentAccountId, useCurrentAccount } from '@/account/currentAccount'
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '@/components/ui/select'

const ADD_ACCOUNT = 'add-account'

// Header dropdown: which account (Amazon seller account) the user is working in, plus "Add account".
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
      <SelectTrigger className="w-56" aria-label="Current account">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {accounts.map(({ account }) => (
          <SelectItem key={account.id} value={account.id}>
            {account.name}
          </SelectItem>
        ))}
        <SelectSeparator />
        <SelectItem value={ADD_ACCOUNT}>+ Add account</SelectItem>
      </SelectContent>
    </Select>
  )
}

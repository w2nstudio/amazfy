import { regionLabel } from '@/account/amazon'
import { Region } from '@/api/generated/model'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/**
 * "Which Amazon region is this seller account in?" for sign-up and "Add Amazon seller account".
 * No default on purpose: the region can't change later, so the seller picks it themselves.
 */
export function RegionField({
  value,
  onChange,
  missing,
}: {
  value: Region | undefined
  onChange: (region: Region) => void
  /** True after a submit without a region: shows what's missing until one is chosen. */
  missing: boolean
}) {
  const showMissing = missing && !value
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor="region">Amazon region</Label>
      <Select value={value ?? ''} onValueChange={(v) => onChange(v as Region)}>
        <SelectTrigger id="region" className="w-full" aria-invalid={showMissing ? true : undefined}>
          <SelectValue placeholder="Choose the region" />
        </SelectTrigger>
        <SelectContent>
          {Object.values(Region).map((region) => (
            <SelectItem key={region} value={region}>
              {regionLabel(region)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {showMissing ? (
        <p className="text-sm text-destructive">Choose the region of this Amazon seller account.</p>
      ) : (
        <p className="text-xs text-muted-foreground">
          Where this Amazon seller account sells. It can&apos;t change later; for another region, add another Amazon
          seller account.
        </p>
      )}
    </div>
  )
}

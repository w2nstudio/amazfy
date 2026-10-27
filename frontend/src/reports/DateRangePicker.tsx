import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { lastDays, PRESET_DAYS, type DateRange } from '@/reports/dateRange'

// Choose the days a report covers: a preset, or custom first and last days (the backend checks them, at most 366).
export function DateRangePicker({ range, onChange }: { range: DateRange; onChange: (range: DateRange) => void }) {
  const [custom, setCustom] = useState(range.days === undefined)

  return (
    <div className="flex flex-col items-start gap-2 sm:items-end">
      <div className="flex flex-wrap gap-1" role="group" aria-label="Date range">
        {PRESET_DAYS.map((days) => (
          <Button
            key={days}
            size="sm"
            variant={!custom && range.days === days ? 'default' : 'outline'}
            onClick={() => {
              setCustom(false)
              onChange(lastDays(days))
            }}
          >
            Last {days} days
          </Button>
        ))}
        <Button size="sm" variant={custom ? 'default' : 'outline'} onClick={() => setCustom(true)}>
          Custom
        </Button>
      </div>
      {custom && (
        <div className="flex items-center gap-2 text-sm">
          <Input
            type="date"
            aria-label="First day"
            className="w-auto"
            value={range.from}
            max={range.to}
            onChange={(e) => e.target.value && onChange({ from: e.target.value, to: range.to })}
          />
          <span className="text-muted-foreground">to</span>
          <Input
            type="date"
            aria-label="Last day"
            className="w-auto"
            value={range.to}
            min={range.from}
            onChange={(e) => e.target.value && onChange({ from: range.from, to: e.target.value })}
          />
        </div>
      )}
    </div>
  )
}

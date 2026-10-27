import { useState } from 'react'
import { BreakdownTable } from '@/reports/BreakdownTable'
import type { DateRange } from '@/reports/dateRange'
import { STARTS, type DrillStep } from '@/reports/drillDown'
import { TrendChart } from '@/reports/TrendChart'

// The dashboard parts that follow the drill-down: the trend chart shows the numbers per day of what the table splits
// up, e.g. after clicking AquaLife the chart is AquaLife's and the table lists AquaLife's products.
export function DrillDownReports({ accountId, range }: { accountId: string; range: DateRange }) {
  const [path, setPath] = useState<DrillStep[]>([STARTS[0]])
  const step = path[path.length - 1]

  return (
    <>
      <TrendChart
        accountId={accountId}
        range={range}
        filter={step.filter}
        title={path.length === 1 ? 'Whole seller account' : step.label}
      />
      <BreakdownTable accountId={accountId} range={range} path={path} onPathChange={setPath} />
    </>
  )
}

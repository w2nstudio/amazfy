import {
  BreakdownLevel,
  BreakdownRowKind,
  type BreakdownRow,
  type GetReportBreakdownParams,
} from '@/api/generated/model'

// The drill-down (requirement A12): brand → product → campaign → ad group → ad. Each step is one table: the level of
// its rows and the filter that selects its ads. Clicking a row adds that row to the filter and goes one level down,
// so the numbers of the next table add up to the clicked row.

/** Which ads count: the breakdown's filters (the trend takes the same ones). */
export type DrillFilter = Omit<GetReportBreakdownParams, 'by' | 'from' | 'to'>

export type DrillStep = {
  by: BreakdownLevel
  filter: DrillFilter
  /** Shown in the path above the table, e.g. "AquaLife" */
  label: string
}

/** Where a drill-down can start ("View by"). */
export const STARTS: DrillStep[] = [
  { by: BreakdownLevel.BRAND, filter: {}, label: 'All brands' },
  { by: BreakdownLevel.PRODUCT, filter: {}, label: 'All products' },
  { by: BreakdownLevel.CAMPAIGN, filter: {}, label: 'All campaigns' },
]

export const LEVEL_NAMES: Record<BreakdownLevel, string> = {
  BRAND: 'Brand',
  PRODUCT: 'Product',
  CAMPAIGN: 'Campaign',
  AD_GROUP: 'Ad group',
  AD: 'Ad',
}

/** The step a click on `row` leads to, or undefined when nothing is below it (an ad, unmatched numbers). */
export function drillInto(row: BreakdownRow, from: DrillStep): DrillStep | undefined {
  const step = (by: BreakdownLevel, filter: DrillFilter) => ({
    by,
    filter: { ...from.filter, ...filter },
    label: row.name,
  })
  switch (row.kind) {
    case BreakdownRowKind.BRAND:
      return step(BreakdownLevel.PRODUCT, { brandId: row.id })
    case BreakdownRowKind.PRODUCT:
      return step(BreakdownLevel.CAMPAIGN, { productId: row.id })
    case BreakdownRowKind.BRAND_LEVEL: // the brand's Brand Store ads
      return step(BreakdownLevel.CAMPAIGN, { brandId: row.id, brandLevel: true })
    case BreakdownRowKind.NOT_LINKED:
      return step(BreakdownLevel.CAMPAIGN, { notLinked: true })
    case BreakdownRowKind.CAMPAIGN:
      return step(BreakdownLevel.AD_GROUP, { campaignId: row.id })
    case BreakdownRowKind.AD_GROUP:
      return step(BreakdownLevel.AD, { adGroupId: row.id })
    default:
      return undefined
  }
}

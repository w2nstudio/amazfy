// How reports show numbers, shared by the tiles and the tables. "—" marks a metric that can't be calculated (the
// backend leaves it out, e.g. no ROAS without spend).

export function money(amount: number | undefined, currency: string): string {
  return amount === undefined ? '—' : new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
}

export function count(n: number): string {
  return new Intl.NumberFormat().format(n)
}

/** ROAS: 2.49 (each 1 spent brought 2.49 in sales) */
export function ratio(value: number | undefined): string {
  return value === undefined ? '—' : value.toFixed(2)
}

export function percent(value: number | undefined): string {
  return value === undefined ? '—' : `${value.toFixed(2)}%`
}

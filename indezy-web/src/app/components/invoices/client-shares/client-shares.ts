import { Invoice } from '../../../models';

/** One client's part of the revenue invoiced over the period. */
export interface ClientShare {
  clientId: number;
  clientName: string;
  /** Revenue excluding VAT, less its credit notes. */
  revenue: number;
  /** Out of 100, rounded. */
  percent: number;
}

/** A client paying this share of the revenue or more makes the freelancer dependent on it. */
export const DEPENDENCE_PERCENT = 50;

/**
 * Each client's share of the revenue invoiced in the 12 months before {@code today}: the invoices issued (paid or not)
 * by issue date, less the credit notes issued in the period, excluding VAT; the largest share first.
 */
export function clientShares(invoices: Invoice[], today: Date = new Date()): ClientShare[] {
  const since = new Date(today);
  since.setFullYear(since.getFullYear() - 1);
  const from = since.toISOString().slice(0, 10);
  const byClient = new Map<number, { name: string; revenue: number }>();
  for (const invoice of invoices) {
    if (invoice.status === 'DRAFT' || !invoice.issueDate || invoice.issueDate <= from) {
      continue;
    }
    const sign = invoice.kind === 'CREDIT_NOTE' ? -1 : 1;
    const entry = byClient.get(invoice.clientId) ?? { name: invoice.clientName ?? '', revenue: 0 };
    entry.revenue += sign * invoice.totalExcludingVat;
    byClient.set(invoice.clientId, entry);
  }
  const total = [...byClient.values()].reduce((sum, entry) => sum + Math.max(entry.revenue, 0), 0);
  if (total <= 0) {
    return [];
  }
  return [...byClient.entries()]
    .filter(([, entry]) => entry.revenue > 0)
    .map(([clientId, entry]) => ({ clientId, clientName: entry.name, revenue: entry.revenue, percent: Math.round(entry.revenue * 100 / total) }))
    .sort((a, b) => b.revenue - a.revenue);
}

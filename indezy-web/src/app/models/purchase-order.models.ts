/** How close a purchase order's work came to its ceiling: under 80 %, from 80 %, or over it. */
export type PurchaseOrderLevel = 'OK' | 'NEAR' | 'EXCEEDED';

/** An amendment as listed under its order: days or euros added, or a later end. */
export interface PurchaseOrderAmendment {
  id: number;
  number: string;
  periodEnd: string | null;
  dayCeiling: number | null;
  amountCeiling: number | null;
  notes: string | null;
}

/** What an order allows with its amendments, and what the mission's activity reports used of it. */
export interface PurchaseOrderUsage {
  dayCeiling: number | null;
  amountCeiling: number | null;
  periodEnd: string | null;
  workedDays: number;
  /** The days worked at the mission's daily rate; none without a rate. */
  amount: number | null;
  /** The share of the closest ceiling used, 1 for all of it; none when no ceiling can be compared. */
  share: number | null;
  daysLeft: number | null;
  level: PurchaseOrderLevel;
}

export interface PurchaseOrder {
  id: number;
  projectId: number;
  number: string;
  periodStart: string;
  periodEnd: string | null;
  dayCeiling: number | null;
  amountCeiling: number | null;
  notes: string | null;
  amendments: PurchaseOrderAmendment[];
  usage: PurchaseOrderUsage;
}

/** An order, or an amendment to one (amendsId, without a start of its own). */
export interface PurchaseOrderWrite {
  amendsId: number | null;
  number: string;
  periodStart: string | null;
  periodEnd: string | null;
  dayCeiling: number | null;
  amountCeiling: number | null;
  notes: string | null;
}

/** A running order close to its ceiling, or over it, among the dashboard's reminders. */
export interface PurchaseOrderAlert {
  orderId: number;
  projectId: number;
  projectRole: string;
  number: string;
  percentUsed: number;
  daysLeft: number | null;
  exceeded: boolean;
}

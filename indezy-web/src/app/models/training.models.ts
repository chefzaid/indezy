import { ComplianceValidity } from './compliance.models';

/** Who a training's funding is asked to: the FIF PL (liberal professions), the AGEFICE (traders), an OPCO, or another body. */
export const TRAINING_FUNDS = ['FIF_PL', 'AGEFICE', 'OPCO', 'OTHER'] as const;
export type TrainingFund = typeof TRAINING_FUNDS[number];

export const FUNDING_STATUSES = ['TO_REQUEST', 'REQUESTED', 'ACCEPTED', 'REFUSED', 'PAID'] as const;
export type FundingStatus = typeof FUNDING_STATUSES[number];

/** A training as written; only its title is required. */
export interface TrainingDetails {
  title: string;
  provider?: string | null;
  startsOn?: string | null;
  endsOn?: string | null;
  hours?: number | null;
  cost?: number | null;
  certification?: string | null;
  certifiedOn?: string | null;
  certificationExpiresOn?: string | null;
  fund?: TrainingFund | null;
  fundingStatus?: FundingStatus | null;
  /** The last day the fund takes the request, as its rules of the year set it. */
  fundingDeadline?: string | null;
  fundedAmount?: number | null;
  notes?: string | null;
}

/** A training as shown: its certification's validity, and whether its funding request is due within 14 days or late. */
export interface Training extends TrainingDetails {
  id: number;
  certificationValidity?: ComplianceValidity | null;
  fundingDue: boolean;
}

// Client related interfaces and types

import { ActivityReportLayout, DocumentLanguage } from './activity-report.models';

/** How the client wants activity reports printed; unset fields use the defaults. */
export interface ActivityReportTemplateFields {
  activityReportTitle?: string;
  activityReportLayout?: ActivityReportLayout;
  activityReportShowsLocation?: boolean;
  activityReportLanguage?: DocumentLanguage;
  activityReportFooter?: string;
}

/** What the client's invoices state of it. */
export interface ClientBillingFields {
  /** 9 digits. */
  siren?: string;
  /** Intra-community VAT number, starting with the country code. */
  vatNumber?: string;
}

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'ESN';

export interface ClientDto extends ActivityReportTemplateFields, ClientBillingFields {
  id: number;
  companyName: string;
  address?: string;
  city: string;
  domain?: string;
  isFinal: boolean;
  notes?: string;
  rating?: number;
  isBlacklisted?: boolean;
  blacklistReason?: string;
  freelanceId?: number;
  totalProjects?: number;
  totalContacts?: number;
  averageDailyRate?: number;
  // Legacy properties for compatibility
  name?: string;
  industry?: string;
  website?: string;
  status?: ClientStatus;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface CreateClientDto extends ActivityReportTemplateFields, ClientBillingFields {
  companyName: string;
  address?: string;
  city: string;
  domain?: string;
  isFinal: boolean;
  notes?: string;
  rating?: number;
  isBlacklisted?: boolean;
  blacklistReason?: string;
  freelanceId?: number;
  // Legacy properties for compatibility
  name?: string;
  industry?: string;
  website?: string;
  status?: ClientStatus;
}

export interface UpdateClientDto extends Partial<CreateClientDto> {
  id: number;
}

/** Whether a company is still in business: closed in the public directory, or under insolvency proceedings in the BODACC. */
export interface CompanyHealth {
  /** False without a SIREN, or when neither source answered. */
  checked: boolean;
  closed: boolean;
  procedure?: 'SAUVEGARDE' | 'REDRESSEMENT' | 'LIQUIDATION' | 'OTHER' | null;
  procedureOn?: string | null;
}

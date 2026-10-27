/** Features an administrator can turn off without redeploying. */
export type FeatureFlag = 'OFFER_FEED' | 'PUBLIC_PROFILES' | 'DATA_IMPORT' | 'YEARLY_REVIEW';

/** What the signed-in account may open. */
export interface Capabilities {
  admin: boolean;
  features: Record<FeatureFlag, boolean>;
}

export interface AdminOverview {
  accounts: number;
  disabledAccounts: number;
  signupsLast30Days: number;
  /** Workspaces using each feature, or accounts with each setting on. */
  featureWorkspaces: Record<string, number>;
}

export interface AdminAccount {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  disabledAt?: string | null;
  deleted: boolean;
  admin: boolean;
}

export interface AdminAccountPage {
  accounts: AdminAccount[];
  total: number;
  page: number;
  size: number;
}

export interface FeatureState {
  flag: FeatureFlag;
  enabled: boolean;
}

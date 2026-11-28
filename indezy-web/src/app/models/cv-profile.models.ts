import { DocumentLanguage } from './activity-report.models';

/** A won mission or past position a CV profile shows, by its id, with its own wording or none. */
export interface CvProfileItem {
  id: number;
  text: string | null;
}

/** What a CV profile is set to; a list left null shows all of the profile's, in its order. */
export interface CvProfileDetails {
  name: string;
  language: DocumentLanguage;
  headline: string | null;
  summary: string | null;
  skills: string[] | null;
  missions: CvProfileItem[] | null;
  positions: CvProfileItem[] | null;
}

/** A CV profile: one positioning of the profile, drawn as its own dossier de compétences. */
export interface CvProfile extends CvProfileDetails {
  id: number;
  isDefault: boolean;
  updatedAt: string | null;
  /** The same profile in the other language, kept in step by Translate (AI); none without one. */
  twinId?: number | null;
  /** Kept for what it was sent with, out of the default and of the limit. */
  archived?: boolean;
  /** What changed in the facts it relies on since it was last saved or reviewed; empty when it is in step. */
  changes?: CvProfileChange[];
}

/** A change in a fact a CV profile relies on. */
export interface CvProfileChange {
  kind: 'SKILL_REMOVED' | 'SKILL_ADDED' | 'CERTIFICATION_ADDED' | 'CERTIFICATION_REMOVED' | 'ITEM_CHANGED' | 'ITEM_REMOVED' | 'ITEM_ADDED';
  label: string | null;
  /** For an item changed: which of "title", "dates", "stack" did. */
  fields: string[];
}

/** A won mission or past position of the profile, to pick and word. */
export interface CvProfileSource {
  id: number;
  title: string | null;
  description: string | null;
}

/** What a CV profile is written from. */
export interface CvProfileSources {
  headline: string | null;
  summary: string | null;
  skills: string[];
  missions: CvProfileSource[];
  positions: CvProfileSource[];
}

/** A link a CV profile is shared through. */
export interface CvShare {
  id: number;
  profileId: number;
  profileName: string | null;
  recipient: string;
  layout: 'NAMED' | 'ANONYMOUS';
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  active: boolean;
  openCount: number;
  downloadCount: number;
  lastOpenedAt: string | null;
}

/** A link just made, with its token shown this once. */
export interface CreatedCvShare {
  share: CvShare;
  token: string;
}

/** What a shared CV link shows, without an account. */
export interface PublicCvShare {
  name: string;
  headline: string | null;
  language: DocumentLanguage;
  layout: 'NAMED' | 'ANONYMOUS';
  expiresAt: string;
}

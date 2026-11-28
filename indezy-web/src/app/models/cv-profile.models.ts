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

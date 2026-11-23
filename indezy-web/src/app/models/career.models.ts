/** A past position as written; it stays off the public profile unless shown. */
export interface ExperienceDetails {
  employer: string;
  title: string;
  startsOn?: string | null;
  /** Empty while it runs. */
  endsOn?: string | null;
  description?: string | null;
  stack?: string | null;
  shownPublicly: boolean;
}

/** A past position of the profile (see docs/features.md#experience-and-education). */
export interface Experience extends ExperienceDetails {
  id: number;
}

/** A school or degree as written. */
export interface EducationDetails {
  school: string;
  degree?: string | null;
  startYear?: number | null;
  endYear?: number | null;
  shownPublicly: boolean;
}

export interface Education extends EducationDetails {
  id: number;
}

import { SkillLevel } from './user-management.models';

/** Whether the public profile is shown, and the address name it has at /p/{slug}. */
export interface PublicProfileSettings {
  enabled: boolean;
  slug: string | null;
}

/** A freelancer's profile as anyone with the link sees it. */
export interface PublicProfile {
  firstName: string;
  lastName: string;
  position?: string;
  city?: string;
  bio?: string;
  avatar?: string;
  skills: { name: string; level?: SkillLevel; years?: number }[];
  languages: string[];
  website?: string;
  linkedin?: string;
  github?: string;
  availableFrom?: string;
  availableNow: boolean;
}

/** An address name built from a name: lower case, no accents, words joined by dashes, at most 40 characters. */
export function slugFrom(text: string): string {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/, '');
}

export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

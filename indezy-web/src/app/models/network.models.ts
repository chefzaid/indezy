/** Lower-case letters, digits and underscores, 3 to 30, starting with a letter: a member's handle. */
export const HANDLE_PATTERN = /^[a-z][a-z0-9_]{2,29}$/;

/** What the member card shows of the profile, each on or off, and whether the member works remotely. */
export interface NetworkFields {
  photo: boolean;
  headline: boolean;
  stack: boolean;
  location: boolean;
  availability: boolean;
  languages: boolean;
  remote: boolean;
}

/**
 * How a card tells the availability: AUTO from the won missions (stored only), available (from a day or now), on a
 * mission (until a day or with no end said), or open to hear of a mission.
 */
export type NetworkAvailability = 'AUTO' | 'AVAILABLE' | 'ON_MISSION' | 'LISTENING';

/** The availability as the member sets it: the mode, and the day it names when available or on a mission. */
export interface NetworkAvailabilitySetting {
  mode: NetworkAvailability;
  date: string | null;
}

/** What members see of a member: the name always, the rest as the member chose. */
export interface MemberCard {
  handle: string;
  firstName: string;
  lastName: string;
  avatar: string | null;
  headline: string | null;
  stack: string[];
  city: string | null;
  remote: boolean;
  /** The status the card shows, never AUTO, and the day it names; null when hidden or unknown. */
  availability: Exclude<NetworkAvailability, 'AUTO'> | null;
  availabilityDate: string | null;
  languages: string[];
  /** The public registry names the member for their billing SIRET. */
  verified: boolean;
}

/**
 * A member's page as a member sees it: the card, the counts, the skills shared with the viewer, who follows whom, and
 * whether it is the viewer's own.
 */
export interface MemberPage {
  card: MemberCard;
  followers: number;
  following: number;
  sharedSkills: string[];
  followed: boolean;
  followsYou: boolean;
  own: boolean;
  /** The viewer mutes them: their posts are hidden, the follow stays. */
  muted: boolean;
  /** The viewer blocks them: no follow either way, and the viewer's page hidden from them. */
  blocked: boolean;
}

/** What the member directory is asked for; every criterion is optional. */
export interface DirectorySearch {
  skill?: string | null;
  city?: string | null;
  availability?: Exclude<NetworkAvailability, 'AUTO'> | null;
  remote?: boolean | null;
  page?: number;
}

/** A page of the member directory, and how many members match in all. */
export interface MemberDirectory {
  members: MemberCard[];
  total: number;
  page: number;
  size: number;
}

/** The members the viewer mutes and blocks. */
export interface NetworkRestrictions {
  muted: MemberCard[];
  blocked: MemberCard[];
}

/** The account's membership: whether it joined, its handle (a suggested one before), its fields and its card once joined. */
export interface NetworkMembership {
  joined: boolean;
  handle: string;
  fields: NetworkFields;
  availability: NetworkAvailabilitySetting;
  card: MemberCard | null;
}

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

/** A post as members read it. */
export interface NetworkPost {
  id: number;
  author: MemberCard;
  text: string;
  tags: string[];
  createdAt: string;
  own: boolean;
  /** How many members like it, and whether the reader does. */
  likes: number;
  liked: boolean;
  /** How many comments the reader sees under it, and whether its author closed them. */
  comments: number;
  commentsClosed: boolean;
  /** Its author changed its text: its earlier versions are kept. */
  edited: boolean;
  /** The reader saved it to their private list. */
  bookmarked: boolean;
  /** For a repost, the post it reposts; unavailable once gone or out of the reader's reach. */
  repostOf: NetworkOriginal | null;
  /** How many reposts it has, and whether its author turned them off. */
  reposts: number;
  repostsOff: boolean;
}

/** The post a repost reposts, as the reader can read it. */
export interface NetworkOriginal {
  id: number;
  author: MemberCard | null;
  text: string | null;
  createdAt: string | null;
  available: boolean;
}

/** A tag's page: whether the reader follows it, its posts, and members whose cards show the skill. */
export interface TagPage {
  tag: string;
  followed: boolean;
  posts: NetworkPosts;
  members: MemberCard[];
}

/** A page of the reader's saved posts, and the tags of all of them, to filter by. */
export interface SavedPosts {
  page: NetworkPosts;
  tags: string[];
}

/** An earlier text of an edited post, and when it was replaced. */
export interface NetworkPostVersion {
  text: string;
  replacedAt: string;
}

/** How long after posting, in milliseconds, a post can still be edited. */
export const POST_EDIT_WINDOW = 15 * 60 * 1000;

/** A comment as the reader sees it, with its replies when it is under the post itself. */
export interface NetworkComment {
  id: number;
  author: MemberCard;
  text: string;
  createdAt: string;
  own: boolean;
  /** Hidden by the post's author: only they see it. */
  hidden: boolean;
  pinned: boolean;
  replies: NetworkComment[];
}

/** A page of posts, the newest first, and whether more follow. */
export interface NetworkPosts {
  posts: NetworkPost[];
  page: number;
  more: boolean;
}

/** A member to follow, and why: followed by members the reader follows, the same skills, or the same city. */
export interface MemberSuggestion {
  card: MemberCard;
  reason: 'FOLLOWED_BY' | 'SAME_STACK' | 'SAME_CITY';
  followedBy: number;
  followedByName: string | null;
  sharedSkills: string[];
}

/** What a network notification tells. */
export type NetworkNotificationType = 'FOLLOW' | 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'REPOST';

/** Notifications grouped as they read: one kind about one post, up to three of the members who did it, and how many did. */
export interface NotificationGroup {
  type: NetworkNotificationType;
  postId: number | null;
  postExcerpt: string | null;
  actors: MemberCard[];
  count: number;
  latestAt: string;
  read: boolean;
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

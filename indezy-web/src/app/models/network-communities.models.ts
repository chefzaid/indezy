import { MemberCard } from './network.models';

/** A community as a member reads it: how many are in it, whether they are, and whether they moderate it. */
export interface Community {
  slug: string;
  name: string;
  description: string | null;
  members: number;
  joined: boolean;
  moderator: boolean;
  /** Joined on request, its posts read by its members alone. */
  privateCommunity: boolean;
  /** The reader asked to join it and waits for its moderators. */
  requested: boolean;
}

/** A community's page: the community, its moderators and whether the reader created it (who names moderators). */
export interface CommunityPage {
  community: Community;
  moderators: MemberCard[];
  creator: boolean;
}

/** A member of a community, and whether they moderate it. */
export interface CommunityMember {
  card: MemberCard;
  moderator: boolean;
}

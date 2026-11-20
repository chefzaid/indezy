import { LinkPreview, MemberCard, Reaction, ReactionCount } from './network.models';
import { NetworkFile } from './network-files.models';

/** Why no message can go in a conversation: the other member does not follow the reader, either blocks the other, or left. */
/** Why the reader cannot write: no follow either way, a block, a member gone, or a message request waiting (sent or received). */
export type ConversationClosed = 'NOT_FOLLOWED' | 'BLOCKED' | 'LEFT' | 'REQUEST_SENT' | 'REQUEST_RECEIVED';

/** A conversation in the reader's list: with another member, or a group (`with` then none). */
export interface Conversation {
  id: number;
  with: MemberCard | null;
  group: ConversationGroup | null;
  last: { mine: boolean; text: string; deleted: boolean; sentAt: string } | null;
  unread: number;
  muted: boolean;
  archived: boolean;
  closed: ConversationClosed | null;
  lastMessageAt: string | null;
  /** When the other member of a conversation between two last read it, while both show read receipts. */
  seenAt?: string | null;
  /** Between two members, whether the other has the app open now, while both show it. */
  online?: boolean;
}

/** Whether the member shows and sees read receipts and who is typing, both ways. */
export interface MessagingSettings {
  readReceipts: boolean;
  typing: boolean;
  /** Whether the member shows and sees who is online, both ways. */
  presence: boolean;
  /** Whether members neither following the other may write to the member, as a request they answer. */
  requests?: boolean;
}

/**
 * A group as one of its members reads it: its name, its other members in reach, how many it has in all, its creator's
 * handle, and whether the reader created it (who names it and adds members).
 */
export interface ConversationGroup {
  title: string;
  members: MemberCard[];
  size: number;
  creator: string | null;
  admin: boolean;
}

/** A message as its reader reads it; `from` is its sender, none for the reader's own. */
export interface Message {
  id: number;
  mine: boolean;
  from: MemberCard | null;
  text: string;
  sentAt: string;
  edited: boolean;
  deleted: boolean;
  /** Its sender can still edit or delete it: within 15 minutes of sending it. */
  editable: boolean;
  replyTo: { id: number; mine: boolean; excerpt: string; deleted: boolean } | null;
  reactions: ReactionCount[];
  myReaction: Reaction | null;
  /** An opportunity shared as a card; for its recipient, the opportunity it became once added to their pipeline. */
  card: OpportunityCard | null;
  /** The card of its first link, once its page was read. */
  preview?: LinkPreview | null;
  /** Its images and documents, in their order. */
  files?: NetworkFile[];
}

/** An opportunity shared in a message: its facts, never its client. */
export interface OpportunityCard {
  role: string;
  dailyRate: number | null;
  city: string | null;
  startDate: string | null;
  remoteDaysPerMonth: number | null;
  stack: string[];
  takenProjectId: number | null;
}

/** A conversation with a page of its messages, the oldest first, and whether older ones remain. */
export interface MessageThread {
  conversation: Conversation;
  messages: Message[];
  more: boolean;
}

/** A message found by a search, with its conversation and the other member. */
export interface FoundMessage {
  conversationId: number;
  /** The other member's name, or the group's. */
  title: string | null;
  /** Who to show beside it: the other member, or in a group its sender (none for the reader's own). */
  with: MemberCard | null;
  messageId: number;
  mine: boolean;
  excerpt: string;
  sentAt: string;
}

export const MESSAGE_MAX = 3000;

import { MemberCard, Reaction, ReactionCount } from './network.models';

/** Why no message can go in a conversation: the other member does not follow the reader, either blocks the other, or left. */
export type ConversationClosed = 'NOT_FOLLOWED' | 'BLOCKED' | 'LEFT';

/** A conversation in the reader's list. */
export interface Conversation {
  id: number;
  with: MemberCard;
  last: { mine: boolean; text: string; deleted: boolean; sentAt: string } | null;
  unread: number;
  muted: boolean;
  archived: boolean;
  closed: ConversationClosed | null;
  lastMessageAt: string | null;
}

/** A message as its reader reads it. */
export interface Message {
  id: number;
  mine: boolean;
  text: string;
  sentAt: string;
  edited: boolean;
  deleted: boolean;
  /** Its sender can still edit or delete it: within 15 minutes of sending it. */
  editable: boolean;
  replyTo: { id: number; mine: boolean; excerpt: string; deleted: boolean } | null;
  reactions: ReactionCount[];
  myReaction: Reaction | null;
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
  with: MemberCard;
  messageId: number;
  mine: boolean;
  excerpt: string;
  sentAt: string;
}

export const MESSAGE_MAX = 3000;

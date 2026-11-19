import { MemberCard, Message, REACTIONS, Reaction } from '../../../models';

/** A day of the conversation and its messages. */
export interface ThreadDay {
  date: string;
  messages: Message[];
}

/** The messages grouped by the day they were sent, in their order. */
export function byDay(messages: Message[]): ThreadDay[] {
  const days: ThreadDay[] = [];
  for (const message of messages) {
    const date = message.sentAt.slice(0, 10);
    if (days.at(-1)?.date !== date) {
      days.push({ date, messages: [] });
    }
    days.at(-1)!.messages.push(message);
  }
  return days;
}

/** The latest of the reader's messages sent before the other member last read the conversation; none without that time. */
export function seenMessageId(messages: Message[], seenAt: string | null | undefined): number | null {
  return seenAt ? messages.filter(message => message.mine && message.sentAt <= seenAt).at(-1)?.id ?? null : null;
}

export function reactionEmoji(reaction: Reaction): string {
  return REACTIONS.find(item => item.reaction === reaction)!.emoji;
}

export function memberNames(members: MemberCard[]): string {
  return members.map(member => `${member.firstName} ${member.lastName}`).join(', ');
}

/** The network post a message's text links to (a post sent in a message), if any: its id. */
export function sharedPostId(text: string | null | undefined): number | null {
  const found = /(?:^|\s)(?:https?:\/\/[^\s/]+)?\/network\/posts\/(\d+)(?=\s|$)/.exec(text ?? '');
  return found ? Number(found[1]) : null;
}

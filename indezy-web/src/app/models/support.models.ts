/** What a request to support is about. */
export type SupportKind = 'PROBLEM' | 'FEATURE' | 'QUESTION' | 'ACCOUNT';

export const SUPPORT_KINDS: SupportKind[] = ['PROBLEM', 'FEATURE', 'QUESTION', 'ACCOUNT'];

/** A member's request to support, with its answer once given. */
export interface SupportRequest {
  id: number;
  kind: SupportKind;
  subject: string;
  description: string;
  context: string | null;
  status: 'OPEN' | 'ANSWERED' | 'CLOSED';
  answer: string | null;
  createdAt: string;
  answeredAt: string | null;
}

/** A request as the administrators see it, with its author's address. */
export interface ReceivedSupportRequest {
  request: SupportRequest;
  userId: number;
  email: string | null;
}

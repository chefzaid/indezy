/** What a request to support is about. */
export type SupportKind = 'PROBLEM' | 'FEATURE' | 'QUESTION' | 'ACCOUNT';

export const SUPPORT_KINDS: SupportKind[] = ['PROBLEM', 'FEATURE', 'QUESTION', 'ACCOUNT'];

/** A screenshot or a PDF sent with a request. */
export interface SupportAttachment {
  id: number;
  requestId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

/** At most this many files go with a request, of this size each. */
export const SUPPORT_FILES_MAX = 3;
export const SUPPORT_FILE_MAX_BYTES = 5 * 1024 * 1024;

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
  attachments: SupportAttachment[];
}

/** A request as the administrators see it, with its author's address. */
export interface ReceivedSupportRequest {
  request: SupportRequest;
  userId: number;
  email: string | null;
}

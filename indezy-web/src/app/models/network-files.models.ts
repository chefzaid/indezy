import { ConfidentialMatch } from './network.models';

/** An image or a document of a post or a message, with its short-lived signed addresses (relative to the API). */
export interface NetworkFile {
  id: number;
  kind: 'IMAGE' | 'DOCUMENT';
  name: string;
  contentType: string;
  size: number;
  /** What an image shows, for those who cannot see it. */
  alt: string | null;
  width: number | null;
  height: number | null;
  /** A PDF's number of pages. */
  pages: number | null;
  url: string;
  /** A PDF's first page drawn; none for another document or an image. */
  previewUrl: string | null;
}

/** A file just uploaded, with the workspace's names its text holds. */
export interface UploadedFile {
  file: NetworkFile;
  confidential: ConfidentialMatch[];
}

/** A file a post or a message carries, with its description when it is an image. */
export interface FileRef {
  id: number;
  alt?: string;
}

export const MAX_POST_IMAGES = 4;
export const MAX_POST_DOCUMENTS = 3;
export const NETWORK_FILE_MAX_BYTES = 50 * 1024 * 1024;
/** What the pickers offer; the server reads the real type from the content. */
export const IMAGE_ACCEPT = 'image/png,image/jpeg,image/gif';
export const DOCUMENT_ACCEPT = '.pdf,.pptx,.xlsx,.docx,.odp,.ods,.odt';

/** Where a testimonial stands: asked through its link, written by its author, shown on the public profile, or kept hidden. */
export type TestimonialStatus = 'REQUESTED' | 'RECEIVED' | 'APPROVED' | 'HIDDEN';

/** A testimonial as its freelancer sees it. */
export interface Testimonial {
  id: number;
  contactId?: number | null;
  recipientName: string;
  /** Where the link was emailed, when it was. */
  recipientEmail?: string | null;
  status: TestimonialStatus;
  expiresAt: string;
  authorName?: string | null;
  authorTitle?: string | null;
  text?: string | null;
  receivedAt?: string | null;
}

/** The asked testimonial and its link's token, shown once, and whether the link went by email. */
export interface TestimonialLink {
  token: string;
  testimonial: Testimonial;
  emailed: boolean;
}

/** Ask a contact, or an external person by their names and the email their link goes to. */
export interface TestimonialRequest {
  contactId?: number | null;
  recipientFirstName?: string | null;
  recipientLastName?: string | null;
  recipientEmail?: string | null;
}

/** What the author sees behind the link. */
export interface PublicTestimonialRequest {
  freelancerName: string;
  recipientName: string;
  answered: boolean;
}

/** The author's testimonial; they agree to be named with it on the public profile. */
export interface TestimonialAnswer {
  authorName: string;
  authorTitle?: string | null;
  text: string;
  consent: boolean;
}

/** An approved testimonial on the public profile. */
export interface ShownTestimonial {
  authorName: string;
  authorTitle?: string | null;
  text: string;
}

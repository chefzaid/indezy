export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Hours of one weekday, in Paris time (HH:mm or HH:mm:ss). */
export interface BookingWindow {
  dayOfWeek: Weekday;
  start: string;
  end: string;
}

export interface BookingSettings {
  enabled: boolean;
  title: string;
  durationMinutes: number;
  bufferMinutes: number;
  minNoticeHours: number;
  maxDaysAhead: number;
  windows: BookingWindow[];
}

/** The booking link's settings and the secret part of its address (none until saved once). */
export interface BookingPage {
  slug: string | null;
  settings: BookingSettings;
}

/** A call booked through the link. */
export interface Booking {
  id: number;
  startsAt: string;
  endsAt: string;
  name: string;
  email: string;
  company: string | null;
  message: string | null;
  cancelledAt: string | null;
}

/** What the link shows a recruiter. */
export interface PublicBookingPage {
  freelancer: string;
  title: string;
  durationMinutes: number;
  timeZone: string;
  days: { date: string; starts: string[] }[];
}

export interface BookingRequest {
  start: string;
  name: string;
  email: string;
  company: string | null;
  message: string | null;
}

export interface BookingConfirmation {
  startsAt: string;
  endsAt: string;
  freelancer: string;
}

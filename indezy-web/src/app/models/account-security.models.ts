/** How a session was opened. */
export type SignInMethod = 'PASSWORD' | 'KEYCLOAK' | 'REGISTRATION';

export type AccountEventType = 'SIGNED_IN' | 'SIGN_IN_REFUSED' | 'SIGNED_OUT' | 'SESSION_REVOKED' | 'OTHER_SESSIONS_REVOKED' |
  'PASSWORD_CHANGED' | 'TWO_FACTOR_ENABLED' | 'TWO_FACTOR_DISABLED' | 'DATA_EXPORTED' | 'DATA_IMPORTED' | 'ACCOUNT_DISABLED' |
  'ACCOUNT_ENABLED';

/** A signed-in browser or device of the account. */
export interface AccountSession {
  id: number;
  method: SignInMethod;
  /** Browser and system, such as "Firefox on Windows"; null when the browser did not say. */
  device: string | null;
  createdAt: string;
  lastSeenAt: string | null;
  expiresAt: string;
  current: boolean;
}

export interface AccountEvent {
  id: number;
  type: AccountEventType;
  /** The sign-in method, why a sign-in was refused, how many sessions ended, or the device of an ended session. */
  detail: string | null;
  device: string | null;
  occurredAt: string;
}

export interface AccountEventPage {
  events: AccountEvent[];
  total: number;
  page: number;
  size: number;
}

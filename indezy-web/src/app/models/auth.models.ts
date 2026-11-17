// Authentication related interfaces and types

export interface LoginRequest {
  email: string;
  password: string;
  /** Authenticator code, sent once the API asked for it. */
  totpCode?: string;
  /** Takes an account on hold before its deletion off hold. */
  restore?: boolean;
}

/** What signing in to an account on hold answers (409): the day it will be deleted for good. */
export interface AccountOnHold {
  accountOnHold: true;
  purgeOn: string;
}

export interface LoginResponse {
  token: string;
  user: {
    id: number;
    accountId?: number;
    email: string;
    firstName: string;
    lastName: string;
    onboardingPending?: boolean;
  };
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface User {
  id: number;
  accountId?: number;
  email: string;
  firstName: string;
  lastName: string;
  role?: string;
  /** A new account that has not finished or skipped its welcome steps yet. */
  onboardingPending?: boolean;
}

// Authentication related interfaces and types

export interface LoginRequest {
  email: string;
  password: string;
  /** Authenticator code, sent once the API asked for it. */
  totpCode?: string;
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

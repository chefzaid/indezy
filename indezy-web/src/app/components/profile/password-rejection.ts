import { HttpErrorResponse } from '@angular/common/http';

/** The password field the server rejected (by name, its stable code) with the error the form shows on it, or null. */
export function rejectedPasswordField(error: unknown): { field: 'currentPassword' | 'confirmPassword'; error: string } | null {
  const fields = error instanceof HttpErrorResponse && error.status === 400 ? error.error?.validationErrors : null;
  if (fields?.currentPassword) {
    return { field: 'currentPassword', error: 'incorrect' };
  }
  if (fields?.confirmPassword) {
    return { field: 'confirmPassword', error: 'passwordMismatch' };
  }
  return null;
}

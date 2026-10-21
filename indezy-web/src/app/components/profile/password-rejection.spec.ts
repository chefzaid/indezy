import { HttpErrorResponse } from '@angular/common/http';
import { rejectedPasswordField } from './password-rejection';

describe('rejectedPasswordField', () => {
  const rejection = (status: number, validationErrors?: Record<string, string>) =>
    new HttpErrorResponse({ status, error: { status, error: 'Validation Failed', validationErrors } });

  it('names the field the server rejected with the error the form shows', () => {
    expect(rejectedPasswordField(rejection(400, { currentPassword: 'Current password is incorrect' })))
      .toEqual({ field: 'currentPassword', error: 'incorrect' });
    expect(rejectedPasswordField(rejection(400, { confirmPassword: 'New password and confirmation do not match' })))
      .toEqual({ field: 'confirmPassword', error: 'passwordMismatch' });
  });

  it('leaves every other failure to the generic message', () => {
    expect(rejectedPasswordField(rejection(400, { newPassword: 'size must be between 8 and 2147483647' }))).toBeNull();
    expect(rejectedPasswordField(rejection(400))).toBeNull();
    expect(rejectedPasswordField(rejection(500, { currentPassword: 'x' }))).toBeNull();
    expect(rejectedPasswordField(new Error('offline'))).toBeNull();
  });
});

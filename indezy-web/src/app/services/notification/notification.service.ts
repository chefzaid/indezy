import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

const DEFAULT_DURATION = 3000;

/**
 * Centralized snackbar notifications: translates the message key and applies
 * consistent duration and styling across the app.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {}

  success(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.open(messageKey, duration, 'success-snackbar', params);
  }

  error(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.open(messageKey, duration, 'error-snackbar', params);
  }

  info(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.open(messageKey, duration, 'info-snackbar', params);
  }

  /** Shows an already-built message (e.g. server-provided text) as a success notification. */
  successText(message: string, duration: number = DEFAULT_DURATION): void {
    this.openText(message, duration, 'success-snackbar');
  }

  /** Shows an already-built message (e.g. server-provided text) as an error notification. */
  errorText(message: string, duration: number = DEFAULT_DURATION): void {
    this.openText(message, duration, 'error-snackbar');
  }

  private open(messageKey: string, duration: number, panelClass: string, params?: Record<string, unknown>): void {
    this.openText(this.translate.instant(messageKey, params), duration, panelClass);
  }

  private openText(message: string, duration: number, panelClass: string): void {
    this.snackBar.open(
      message,
      this.translate.instant('common.close'),
      { duration, panelClass: [panelClass] }
    );
  }
}

import { Injectable, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

const DEFAULT_DURATION = 3000;
/** An error shown with **Report** stays this long, to leave time to choose it. */
const REPORTABLE_DURATION = 6000;
/** Pages open without an account, where **Report** would lead to the sign-in page. */
const PUBLIC_PAGES = /^\/($|en\/?$|login|register|help|legal|p\/|book\/|testimonial|activity-report|network-invitation|emails\/|documents-pack|shared-cv)/;

/**
 * Centralized snackbar notifications: translates the message key and applies
 * consistent duration and styling across the app.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService,
    private readonly injector: Injector
  ) {}

  success(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.open(messageKey, duration, 'success-snackbar', params);
  }

  /** An error, with **Report** opening a request to support about it once signed in. */
  error(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.reportable(this.translate.instant(messageKey, params), duration);
  }

  info(messageKey: string, duration: number = DEFAULT_DURATION, params?: Record<string, unknown>): void {
    this.open(messageKey, duration, 'info-snackbar', params);
  }

  /** A success notification offering an action (such as an undo) instead of Close; emits when the action is chosen. */
  successWithAction(messageKey: string, actionKey: string, duration: number = DEFAULT_DURATION,
                    params?: Record<string, unknown>): Observable<void> {
    return this.snackBar.open(this.translate.instant(messageKey, params), this.translate.instant(actionKey),
      { duration, panelClass: ['success-snackbar'] }).onAction();
  }

  /** Shows an already-built message (e.g. server-provided text) as a success notification. */
  successText(message: string, duration: number = DEFAULT_DURATION): void {
    this.openText(message, duration, 'success-snackbar');
  }

  /** Shows an already-built message (e.g. server-provided text) as an error notification. */
  errorText(message: string, duration: number = DEFAULT_DURATION): void {
    this.reportable(message, duration);
  }

  private reportable(message: string, duration: number): void {
    const page = globalThis.location?.pathname ?? '/';
    if (PUBLIC_PAGES.test(page)) {
      this.openText(message, duration, 'error-snackbar');
      return;
    }
    // The router is asked for when needed: it is not yet built while the app's first services are.
    this.snackBar.open(message, this.translate.instant('support.report'), { duration: Math.max(duration, REPORTABLE_DURATION),
      panelClass: ['error-snackbar'] }).onAction().subscribe(() => {
      void this.injector.get(Router).navigate(['/support'], { queryParams: { kind: 'PROBLEM', error: message.slice(0, 300), page } });
    });
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

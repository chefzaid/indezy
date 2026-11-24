import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { InboxWatcher } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Profile → Preferences → **Inbox watcher** (docs/features.md#inbox-watcher): the member's own address to forward their
 * recruiters' emails to, how to forward them, and what the AI did with the latest ones. Hidden while Indezy cannot
 * receive emails.
 */
@Component({
  selector: 'app-inbox-watcher',
  imports: [DatePipe, RouterModule, MatButtonModule, MatCardModule, MatIconModule, TranslateModule],
  template: `
    @if (watcher?.available) {
      <mat-card class="form-section inbox-watcher" data-inbox-watcher>
        <mat-card-header>
          <mat-card-title><mat-icon aria-hidden="true">forward_to_inbox</mat-icon>{{ 'inboxWatcher.title' | translate }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p class="hint">{{ 'inboxWatcher.intro' | translate }}</p>
          @if (watcher!.address; as address) {
            <div class="address">
              <code data-inbox-address>{{ address }}</code>
              <button mat-icon-button type="button" (click)="copy(address)" data-inbox-copy
                [attr.aria-label]="'inboxWatcher.copy' | translate"><mat-icon>content_copy</mat-icon></button>
            </div>
            <ol class="steps">
              <li>{{ 'inboxWatcher.steps.gmail' | translate }}</li>
              <li>{{ 'inboxWatcher.steps.filter' | translate }}</li>
              <li>{{ 'inboxWatcher.steps.byHand' | translate }}</li>
            </ol>
            <button mat-button type="button" (click)="renew()" [disabled]="busy" data-inbox-renew>{{ 'inboxWatcher.renew' | translate }}</button>
          } @else {
            <button mat-flat-button color="primary" type="button" (click)="renew()" [disabled]="busy" data-inbox-create>
              {{ 'inboxWatcher.create' | translate }}
            </button>
          }
          @if (watcher!.recent.length) {
            <h4>{{ 'inboxWatcher.recent' | translate }}</h4>
            <ul class="recent">
              @for (email of watcher!.recent; track email.id) {
                <li [attr.data-inbox-email]="email.kind">
                  <span class="when">{{ email.receivedAt | date:'d MMM, HH:mm' }}</span>
                  <span class="what">{{ email.subject || email.sender }}</span>
                  <span class="done">{{ 'inboxWatcher.kinds.' + email.kind | translate }} · {{ 'inboxWatcher.actions.' + email.action | translate }}</span>
                  @if (email.projectId) {
                    <a [routerLink]="['/projects', email.projectId]" [attr.data-inbox-project]="email.projectId">{{ 'inboxWatcher.open' | translate }}</a>
                  }
                </li>
              }
            </ul>
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [`
    .inbox-watcher { margin-top: 16px; }
    mat-card-title { display: flex; align-items: center; gap: 8px; }
    .hint { color: var(--text-secondary, rgba(0, 0, 0, 0.6)); }
    .address { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .address code { word-break: break-all; padding: 4px 8px; border-radius: 4px; background: var(--surface-variant, rgba(0, 0, 0, 0.05)); }
    .steps { padding-left: 20px; }
    .recent { list-style: none; padding: 0; margin: 0; }
    .recent li { display: flex; flex-wrap: wrap; gap: 4px 12px; padding: 6px 0; border-top: 1px solid var(--divider, rgba(0, 0, 0, 0.08)); }
    .recent .when, .recent .done { color: var(--text-secondary, rgba(0, 0, 0, 0.6)); }
    .recent .what { flex: 1 1 200px; overflow-wrap: anywhere; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InboxWatcherComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly clipboard = inject(Clipboard);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  watcher: InboxWatcher | null = null;
  busy = false;

  ngOnInit(): void {
    this.ai.inboxWatcher().subscribe({ next: watcher => this.show(watcher), error: () => undefined });
  }

  /** Gives the member an address, or a new one in place of the previous. */
  renew(): void {
    this.busy = true;
    this.ai.renewInboxAddress().subscribe({
      next: watcher => {
        this.busy = false;
        this.show(watcher);
      },
      error: () => {
        this.busy = false;
        this.notifications.error('inboxWatcher.failed');
        this.changes.markForCheck();
      }
    });
  }

  copy(address: string): void {
    if (this.clipboard.copy(address)) {
      this.notifications.success('inboxWatcher.copied');
    }
  }

  private show(watcher: InboxWatcher): void {
    this.watcher = watcher;
    this.changes.markForCheck();
  }
}

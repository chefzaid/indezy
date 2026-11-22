import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { ReceivedSupportRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { SupportService } from '../../services/support/support.service';

/** **Admin → Support**: the members' requests, open ones first, each answered (and closed) from here, which emails the member. */
@Component({
  selector: 'app-admin-support',
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslateModule],
  template: `
    @for (entry of requests; track entry.request.id) {
      <article class="request" [attr.data-admin-support]="entry.request.id">
        <header>
          <strong>{{ entry.request.subject }}</strong>
          <span class="status">{{ 'support.statuses.' + entry.request.status | translate }}</span>
        </header>
        <p class="meta">{{ 'support.kinds.' + entry.request.kind | translate }} · {{ entry.email }} · {{ entry.request.createdAt | date:'medium' }}</p>
        <p class="text">{{ entry.request.description }}</p>
        @if (entry.request.context) {
          <pre class="context">{{ entry.request.context }}</pre>
        }
        @if (entry.request.answer) {
          <p class="answer">{{ entry.request.answer }}</p>
        }
        @if (entry.request.status !== 'CLOSED') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>{{ 'admin.support.answer' | translate }}</mat-label>
            <textarea matInput rows="3" [(ngModel)]="drafts[entry.request.id]" maxlength="5000" data-admin-support-answer></textarea>
          </mat-form-field>
          <div class="actions">
            <button mat-stroked-button type="button" (click)="answer(entry, false)" [disabled]="!drafts[entry.request.id]?.trim()">
              {{ 'admin.support.reply' | translate }}
            </button>
            <button mat-flat-button color="primary" type="button" (click)="answer(entry, true)" [disabled]="!drafts[entry.request.id]?.trim()"
              data-admin-support-close>
              {{ 'admin.support.replyAndClose' | translate }}
            </button>
          </div>
        }
      </article>
    } @empty {
      <p class="empty">{{ 'admin.support.none' | translate }}</p>
    }
  `,
  styles: [`
    .request { padding: 12px 0; border-bottom: 1px solid var(--app-border); }
    header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; }
    .meta { margin: 4px 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; overflow-wrap: anywhere; }
    .text, .answer { white-space: pre-line; overflow-wrap: anywhere; }
    .answer { padding-left: 12px; border-left: 3px solid var(--mat-sys-primary); }
    .context { padding: 8px; background: var(--mat-sys-surface-container); font-size: .75rem; white-space: pre-wrap; overflow-wrap: anywhere; }
    .full { width: 100%; }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
    .empty { color: var(--mat-sys-on-surface-variant); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AdminSupportComponent implements OnInit {
  private readonly support = inject(SupportService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  requests: ReceivedSupportRequest[] = [];
  readonly drafts: Record<number, string> = {};

  ngOnInit(): void {
    this.support.received().subscribe({
      next: requests => {
        this.requests = requests;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('admin.support.loadError')
    });
  }

  answer(entry: ReceivedSupportRequest, close: boolean): void {
    const text = this.drafts[entry.request.id]?.trim();
    if (!text) {
      return;
    }
    this.support.answer(entry.request.id, text, close).subscribe({
      next: request => {
        entry.request = request;
        delete this.drafts[request.id];
        this.notifications.success('admin.support.answered');
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('admin.support.answerError')
    });
  }
}

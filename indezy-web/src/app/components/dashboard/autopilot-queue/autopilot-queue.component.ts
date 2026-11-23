import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { AiAutopilotQueue, AiDraftedFollowUp, AiPreparedOffer, AiPreparedPack } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { AiService } from '../../../services/ai/ai.service';

/** How many prepared offers the dashboard lists. */
export const PREPARED_SHOWN = 5;

/**
 * **Prepared overnight** (docs/features.md#daily-autopilot): when the daily autopilot run is on, what it triaged in the
 * last day, those worth applying to first, each opening its offer; nothing while it is off or found nothing.
 */
@Component({
  selector: 'app-autopilot-queue',
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    @if (queue && queue.on && (queue.offers.length || drafts.length || packs.length)) {
      <section class="autopilot" data-autopilot-queue aria-labelledby="autopilot-title">
        <h3 id="autopilot-title"><mat-icon aria-hidden="true">auto_awesome</mat-icon>{{ 'dashboard.autopilot.title' | translate }}</h3>
        <p data-autopilot-summary>{{ 'dashboard.autopilot.summary' | translate:{ count: queue.offers.length, fit: fit, drafts: drafts.length, packs: packs.length } }}</p>
        <ul>
          @for (offer of shown; track offer.offerId) {
            <li [attr.data-autopilot-offer]="offer.offerId" [class.fit]="offer.goodFit">
              <a [routerLink]="['/offers', offer.offerId]">{{ offer.title }}@if (offer.company) { · {{ offer.company }} }</a>
              <span class="verdict">{{ offer.verdict }}</span>
              @if (offer.dealBreakers.length) {
                <span class="breakers">{{ offer.dealBreakers.join(' · ') }}</span>
              }
            </li>
          }
        </ul>
        @if (packs.length) {
          <ul class="drafts">
            @for (pack of packs; track pack.id) {
              <li [attr.data-autopilot-pack]="pack.id">
                <details>
                  <summary>{{ 'dashboard.autopilot.packFor' | translate:{ title: pack.title } }}@if (pack.company) { · {{ pack.company }} }</summary>
                  @if (pack.subject) {
                    <p class="subject">{{ pack.subject }}</p>
                  }
                  <p class="body">{{ pack.message }}</p>
                  @if (pack.screening.length) {
                    <ul class="screening">
                      @for (line of pack.screening; track $index) {
                        <li>{{ line }}</li>
                      }
                    </ul>
                  }
                  @if (pack.rate) {
                    <p class="rate" data-autopilot-pack-rate>{{ 'dashboard.autopilot.rate' | translate:{ rate: pack.rate } }}</p>
                  }
                </details>
                <div class="actions">
                  <button mat-button type="button" (click)="copyPack(pack)" data-autopilot-pack-copy>{{ 'dashboard.autopilot.copy' | translate }}</button>
                  <a mat-button [routerLink]="['/offers', pack.offerId]">{{ 'dashboard.autopilot.openOffer' | translate }}</a>
                  <button mat-button type="button" (click)="dismissPack(pack)" data-autopilot-pack-dismiss>{{ 'dashboard.autopilot.dismiss' | translate }}</button>
                </div>
              </li>
            }
          </ul>
        }
        @if (drafts.length) {
          <ul class="drafts">
            @for (draft of drafts; track draft.id) {
              <li [attr.data-autopilot-draft]="draft.id">
                <details>
                  <summary>{{ 'dashboard.autopilot.draftFor' | translate:{ contact: draft.contact } }}@if (draft.company) { · {{ draft.company }} }</summary>
                  @if (draft.subject) {
                    <p class="subject">{{ draft.subject }}</p>
                  }
                  <p class="body">{{ draft.body }}</p>
                </details>
                <div class="actions">
                  <button mat-button type="button" (click)="copy(draft)" data-autopilot-copy>{{ 'dashboard.autopilot.copy' | translate }}</button>
                  @if (draft.projectId) {
                    <a mat-button [routerLink]="['/projects', draft.projectId]">{{ 'dashboard.autopilot.openOpportunity' | translate }}</a>
                  }
                  <button mat-button type="button" (click)="dismiss(draft)" data-autopilot-dismiss>{{ 'dashboard.autopilot.dismiss' | translate }}</button>
                </div>
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styles: [`
    .autopilot {
      margin: 0 0 16px;
      padding: 12px 16px;
      border: 1px solid var(--app-border);
      border-radius: var(--app-radius, 12px);
      background: var(--app-surface);

      h3 {
        display: flex;
        gap: 8px;
        align-items: center;
        margin: 0 0 4px;
        font-size: 1rem;
      }

      p {
        margin: 0 0 8px;
        color: var(--app-text-muted);
      }

      ul {
        margin: 0;
        padding: 0;
        list-style: none;
      }

      li {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 6px 0;
        border-top: 1px solid var(--app-border);
      }

      li.fit a {
        font-weight: 600;
      }

      a {
        color: var(--app-link);
      }

      .verdict,
      .breakers {
        font-size: 0.875rem;
      }

      .breakers {
        color: var(--app-danger);
      }

      .drafts {
        margin-top: 8px;
      }

      summary {
        cursor: pointer;
      }

      .subject {
        margin: 8px 0 0;
        font-weight: 600;
        color: inherit;
      }

      .body {
        margin: 4px 0 0;
        white-space: pre-line;
        color: inherit;
      }

      .screening {
        margin: 4px 0 0;
        padding-left: 20px;
        list-style: disc;
        font-size: 0.875rem;
      }

      .screening li {
        display: list-item;
        padding: 0;
        border: none;
      }

      .rate {
        margin: 4px 0 0;
        color: inherit;
      }

      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AutopilotQueueComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly clipboard = inject(Clipboard);
  private readonly notifications = inject(NotificationService);

  queue: AiAutopilotQueue | null = null;

  ngOnInit(): void {
    this.ai.autopilot().subscribe({
      next: queue => {
        this.queue = Array.isArray(queue?.offers) ? queue : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** How many are worth applying to. */
  get fit(): number {
    return this.queue?.offers.filter(offer => offer.goodFit).length ?? 0;
  }

  get shown(): AiPreparedOffer[] {
    return this.queue?.offers.slice(0, PREPARED_SHOWN) ?? [];
  }

  get drafts(): AiDraftedFollowUp[] {
    return this.queue?.drafts ?? [];
  }

  get packs(): AiPreparedPack[] {
    return this.queue?.packs ?? [];
  }

  /** Copies the application as it would be sent, its subject first. */
  copyPack(pack: AiPreparedPack): void {
    const text = pack.subject ? pack.subject + '\n\n' + pack.message : pack.message;
    if (this.clipboard.copy(text)) {
      this.notifications.success('dashboard.autopilot.copied');
    }
  }

  /** Sets the application aside, for good. */
  dismissPack(pack: AiPreparedPack): void {
    this.ai.dismissPack(pack.id).subscribe({
      next: () => {
        if (this.queue) {
          this.queue = { ...this.queue, packs: this.packs.filter(kept => kept.id !== pack.id) };
        }
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('dashboard.autopilot.dismissError')
    });
  }

  /** Copies the draft as it would be sent, its subject first. */
  copy(draft: AiDraftedFollowUp): void {
    const text = draft.subject ? draft.subject + '\n\n' + draft.body : draft.body;
    if (this.clipboard.copy(text)) {
      this.notifications.success('dashboard.autopilot.copied');
    }
  }

  /** Sets the draft aside, for good. */
  dismiss(draft: AiDraftedFollowUp): void {
    this.ai.dismissDraft(draft.id).subscribe({
      next: () => {
        if (this.queue) {
          this.queue = { ...this.queue, drafts: this.drafts.filter(kept => kept.id !== draft.id) };
        }
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('dashboard.autopilot.dismissError')
    });
  }
}

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DocumentLanguage } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AvailabilityEmailsComponent } from '../availability-emails/availability-emails.component';

/**
 * Drafts the announcement of the next availability with AI (see docs/features.md#ai-features): a LinkedIn post to copy
 * and a network post to copy or keep as a network draft, both editable; nothing is posted from here. Below, the emails to
 * the ESN contacts worked with, which go only once they are ticked and sent; opened by **Prospect** (dashboard, an
 * opportunity turned down), those emails open at once.
 */
@Component({
  selector: 'app-ai-availability-dialog',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatDialogModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressSpinnerModule, TranslateModule, AvailabilityEmailsComponent],
  template: `
    <h2 mat-dialog-title>{{ 'ai.availability.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'ai.availability.hint' | translate }}</p>
      <div class="row">
        <mat-button-toggle-group [formControl]="language" [attr.aria-label]="'ai.availability.language' | translate">
          <mat-button-toggle value="FR">Français</mat-button-toggle>
          <mat-button-toggle value="EN">English</mat-button-toggle>
        </mat-button-toggle-group>
        <button mat-flat-button color="primary" type="button" (click)="write()" [disabled]="writing" data-ai-availability-write>
          @if (writing) {
            <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
          }
          {{ (linkedin.value ? 'ai.availability.again' : 'ai.availability.write') | translate }}
        </button>
      </div>
      @if (availableFrom) {
        <p class="from" data-ai-availability-from>{{ 'ai.availability.from' | translate:{ date: (availableFrom | date:'longDate') } }}</p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'ai.availability.linkedin' | translate }}</mat-label>
          <textarea matInput [formControl]="linkedin" rows="8" data-ai-availability-linkedin></textarea>
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="copy(linkedin.value)" data-ai-availability-copy-linkedin>
          <mat-icon>content_copy</mat-icon>{{ 'ai.availability.copy' | translate }}
        </button>
        <mat-form-field appearance="outline" class="full network">
          <mat-label>{{ 'ai.availability.network' | translate }}</mat-label>
          <textarea matInput [formControl]="network" rows="4" maxlength="3000" data-ai-availability-network></textarea>
        </mat-form-field>
        <div class="row">
          <button mat-stroked-button type="button" (click)="copy(network.value)">
            <mat-icon>content_copy</mat-icon>{{ 'ai.availability.copy' | translate }}
          </button>
          <button mat-stroked-button type="button" (click)="keepDraft()" [disabled]="keeping || !network.value.trim()" data-ai-availability-draft>
            <mat-icon>edit_note</mat-icon>{{ 'ai.availability.draft' | translate }}
          </button>
        </div>
        <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
      }
      <app-availability-emails [language]="language.value" [availableFrom]="availableFrom" [text]="network.value" [open]="prospect" />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .hint, .from, .disclaimer { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    .from { color: inherit; font-weight: 500; }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 12px; }
    .full { width: 100%; }
    .network { margin-top: 16px; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiAvailabilityDialogComponent {
  private readonly ai = inject(AiService);
  private readonly networkApi = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly changes = inject(ChangeDetectorRef);

  /** Opened by **Prospect**: the emails to the ESN contacts open at once. */
  readonly prospect = !!inject<{ prospect?: boolean } | null>(MAT_DIALOG_DATA, { optional: true })?.prospect;
  readonly language = new FormControl<DocumentLanguage>(inject(TranslateService).currentLang === 'en' ? 'EN' : 'FR', { nonNullable: true });
  readonly linkedin = new FormControl('', { nonNullable: true });
  readonly network = new FormControl('', { nonNullable: true });
  availableFrom: string | null = null;
  writing = false;
  keeping = false;

  write(): void {
    this.writing = true;
    this.ai.availability(this.language.value).subscribe({
      next: announcement => {
        this.writing = false;
        this.availableFrom = announcement.availableFrom;
        this.linkedin.setValue(announcement.linkedin);
        this.network.setValue(announcement.network);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.writing = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  copy(text: string): void {
    if (this.clipboard.copy(text)) {
      this.notifications.success('ai.availability.copied');
    }
  }

  /** Keeps the network post as a draft of the member's, to post from their page once checked. */
  keepDraft(): void {
    const text = this.network.value.trim();
    if (!text || this.keeping) {
      return;
    }
    this.keeping = true;
    this.networkApi.createDraft(text, null).subscribe({
      next: () => {
        this.keeping = false;
        this.notifications.success('ai.availability.drafted');
        this.changes.markForCheck();
      },
      error: () => {
        this.keeping = false;
        this.notifications.error('ai.availability.draftError');
        this.changes.markForCheck();
      }
    });
  }
}

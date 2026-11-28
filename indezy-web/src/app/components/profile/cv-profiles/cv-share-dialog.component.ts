import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { CvProfile, CvShare } from '../../../models';
import { CvShareService } from '../../../services/cv-profile/cv-share.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Shares a CV profile through a link of its own (see docs/features.md#cv-profiles): for whom, named or anonymized, for
 * how many days; the link is shown once, to copy. Lists the profile's links with their openings, to revoke one.
 */
@Component({
  selector: 'app-cv-share-dialog',
  imports: [DatePipe, FormsModule, MatButtonModule, MatButtonToggleModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'cvShares.title' | translate:{ name: profile.name } }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'cvShares.hint' | translate }}</p>
      @if (link) {
        <div class="link" role="status" data-cv-share-link>
          <p>{{ 'cvShares.created' | translate:{ recipient: link.recipient } }}</p>
          <mat-form-field appearance="outline" class="wide" subscriptSizing="dynamic">
            <mat-label>{{ 'cvShares.link' | translate }}</mat-label>
            <input matInput [value]="link.url" readonly data-cv-share-url>
          </mat-form-field>
          <button mat-stroked-button type="button" (click)="copy()" data-cv-share-copy><mat-icon>content_copy</mat-icon>{{ 'cvShares.copy' | translate }}</button>
        </div>
      } @else {
        <mat-form-field appearance="outline" class="wide">
          <mat-label>{{ 'cvShares.recipient' | translate }}</mat-label>
          <input matInput [(ngModel)]="recipient" maxlength="120" required [placeholder]="'cvShares.recipientHint' | translate" data-cv-share-recipient>
        </mat-form-field>
        <div class="row">
          <mat-button-toggle-group [(ngModel)]="layout" [attr.aria-label]="'cvShares.layout' | translate" data-cv-share-layout>
            <mat-button-toggle value="NAMED">{{ 'cvShares.named' | translate }}</mat-button-toggle>
            <mat-button-toggle value="ANONYMOUS">{{ 'cvShares.anonymous' | translate }}</mat-button-toggle>
          </mat-button-toggle-group>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'cvShares.days' | translate }}</mat-label>
            <mat-select [(ngModel)]="days" data-cv-share-days>
              @for (choice of dayChoices; track choice) {
                <mat-option [value]="choice">{{ 'cvShares.forDays' | translate:{ count: choice } }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
      }
      @if (shares.length) {
        <h3>{{ 'cvShares.existing' | translate }}</h3>
        <ul class="shares">
          @for (share of shares; track share.id) {
            <li [attr.data-cv-share]="share.id">
              <div class="text">
                <strong>{{ share.recipient }}</strong>
                <span class="muted">
                  {{ ('cvShares.' + (share.layout === 'NAMED' ? 'named' : 'anonymous')) | translate }} ·
                  @if (share.active) {
                    {{ 'cvShares.until' | translate:{ date: (share.expiresAt | date:'mediumDate') } }}
                  } @else {
                    {{ (share.revokedAt ? 'cvShares.revoked' : 'cvShares.expired') | translate }}
                  }
                  · {{ 'cvShares.seen' | translate:{ opens: share.openCount, downloads: share.downloadCount } }}
                </span>
              </div>
              @if (share.active) {
                <button mat-button type="button" (click)="revoke(share)" [attr.data-cv-share-revoke]="share.id">{{ 'cvShares.revoke' | translate }}</button>
              }
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
      @if (!link) {
        <button mat-flat-button color="primary" type="button" (click)="share()" [disabled]="!recipient.trim() || sharing" data-cv-share-create>
          <mat-icon>link</mat-icon>{{ 'cvShares.create' | translate }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .hint, .muted { color: var(--mat-sys-on-surface-variant); }
    .hint { margin: 0 0 12px; }
    .muted { font-size: .875rem; }
    .wide { width: 100%; }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .link { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; align-items: flex-start; }
    .link p { margin: 0; }
    h3 { margin: 16px 0 4px; font-size: 1rem; }
    .shares { list-style: none; margin: 0; padding: 0; }
    .shares li { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .shares .text { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CvShareDialogComponent {
  private readonly service = inject(CvShareService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly changes = inject(ChangeDetectorRef);
  readonly profile = inject<{ profile: CvProfile }>(MAT_DIALOG_DATA).profile;

  readonly dayChoices = [3, 7, 14, 30];
  recipient = '';
  layout: 'NAMED' | 'ANONYMOUS' = 'NAMED';
  days = 7;
  sharing = false;
  shares: CvShare[] = [];
  /** The link just made, shown this once. */
  link: { recipient: string; url: string } | null = null;

  constructor() {
    this.load();
  }

  private load(): void {
    this.service.list().subscribe({
      next: shares => {
        this.shares = shares.filter(share => share.profileId === this.profile.id);
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('cvShares.loadError')
    });
  }

  share(): void {
    const recipient = this.recipient.trim();
    if (!recipient || this.sharing) {
      return;
    }
    this.sharing = true;
    this.service.create(this.profile.id, recipient, this.layout, this.days).subscribe({
      next: created => {
        this.sharing = false;
        this.link = { recipient: created.share.recipient, url: `${globalThis.location.origin}/shared-cv#${created.token}` };
        this.load();
      },
      error: () => {
        this.sharing = false;
        this.notifications.error('cvShares.createError');
        this.changes.markForCheck();
      }
    });
  }

  copy(): void {
    if (this.link && this.clipboard.copy(this.link.url)) {
      this.notifications.success('cvShares.copied');
    }
  }

  revoke(share: CvShare): void {
    this.service.revoke(share.id).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('cvShares.createError')
    });
  }
}

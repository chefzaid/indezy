import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { PublicCvShare } from '../../models';
import { CvShareService } from '../../services/cv-profile/cv-share.service';
import { NotificationService } from '../../services/notification/notification.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { saveBlob } from '../../shared/utils/download-file';

/**
 * The page an ESN opens from a shared CV link, without an account: whose CV, until when, to download as a PDF or an
 * editable Word document. The token comes after the # of the link, so it never reaches a server log.
 */
@Component({
  selector: 'app-shared-cv',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule, PublicPageHeaderComponent],
  template: `
    <main class="shared-page">
      <app-public-page-header></app-public-page-header>
      <mat-card class="shared-card">
        <mat-card-content>
          @if (loading) {
            <div class="state"><mat-spinner diameter="40" [attr.aria-label]="'common.loading' | translate"></mat-spinner></div>
          } @else if (!shared) {
            <div class="state" data-shared-cv-invalid>
              <mat-icon class="state-icon" aria-hidden="true">link_off</mat-icon>
              <h1>{{ 'cvShares.page.invalidTitle' | translate }}</h1>
              <p>{{ 'cvShares.page.invalidMessage' | translate }}</p>
            </div>
          } @else {
            <h1 data-shared-cv-name>{{ shared.name }}</h1>
            @if (shared.headline) {
              <p class="headline">{{ shared.headline }}</p>
            }
            <p class="until">{{ 'cvShares.page.until' | translate:{ date: (shared.expiresAt | date:'medium') } }}</p>
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="download('PDF')" data-shared-cv-pdf>
                <mat-icon>picture_as_pdf</mat-icon>{{ 'cvShares.page.pdf' | translate }}
              </button>
              <button mat-stroked-button type="button" (click)="download('DOCX')" data-shared-cv-docx>
                <mat-icon>description</mat-icon>{{ 'cvShares.page.word' | translate }}
              </button>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; flex: 1 0 auto; }
    .shared-page { flex: 1 0 auto; background: var(--app-bg); }
    .shared-card { max-width: 640px; margin: 24px auto; }
    .shared-card h1 { margin: 0 0 4px; font-size: 1.4rem; }
    @media (max-width: 759px) { .shared-card { margin: 16px; } }
    .headline { margin: 0 0 8px; font-size: 1.05rem; }
    .until { margin: 0 0 16px; color: var(--mat-sys-on-surface-variant); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .state { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 24px 0; }
    .state-icon { width: 40px; height: 40px; font-size: 40px; color: var(--mat-sys-on-surface-variant); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SharedCvComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CvShareService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  shared: PublicCvShare | null = null;
  loading = true;
  private token = '';

  ngOnInit(): void {
    this.token = (this.route.snapshot.fragment ?? '').trim();
    if (!this.token) {
      this.loading = false;
      return;
    }
    this.service.open(this.token).subscribe({
      next: shared => {
        this.shared = shared;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  download(format: 'PDF' | 'DOCX'): void {
    const shared = this.shared;
    if (!shared) {
      return;
    }
    const word = shared.name.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'cv';
    this.service.dossier(this.token, format).subscribe({
      next: file => saveBlob(file, `cv-${word}.${format === 'DOCX' ? 'docx' : 'pdf'}`),
      error: () => this.notifications.error('cvShares.page.downloadError')
    });
  }
}

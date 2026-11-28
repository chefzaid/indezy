import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { AiService, aiBlobErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { PostingDossierDialogComponent } from './posting-dossier-dialog.component';

type Layout = 'NAMED' | 'ANONYMOUS';
type Language = 'FR' | 'EN';

/**
 * Downloads the dossier de compétences (see docs/features.md#dossier-de-competences): named or anonymized, in French
 * or English, built from the profile, the certifications and the won missions. Given an opportunity or a feed offer, the
 * AI tailors it to that one (see docs/features.md#ai-features); on the profile, to a job posting pasted or read from a PDF.
 */
@Component({
  selector: 'app-dossier-button',
  imports: [MatButtonModule, MatDialogModule, MatDividerModule, MatIconModule, MatMenuModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <button mat-stroked-button type="button" [matMenuTriggerFor]="dossierMenu" [disabled]="busy"
      [attr.data-dossier]="projectId ? 'tailored' : offerId ? 'offer' : 'profile'">
      @if (busy) {
        <mat-progress-spinner mode="indeterminate" diameter="18" [attr.aria-label]="'ai.dossier.busy' | translate"></mat-progress-spinner>
      } @else {
        <mat-icon>{{ tailored ? 'auto_awesome' : 'badge' }}</mat-icon>
      }
      {{ (tailored ? 'ai.dossier.open' : 'profile.dossier.download') | translate }}
    </button>
    <mat-menu #dossierMenu="matMenu">
      @for (choice of choices; track choice.layout + choice.language) {
        <button mat-menu-item type="button" (click)="download(choice.layout, choice.language)"
          [attr.data-dossier-choice]="choice.layout + '-' + choice.language">
          <mat-icon>{{ choice.layout === 'NAMED' ? 'person' : 'visibility_off' }}</mat-icon>
          <span>{{ 'profile.dossier.' + choice.layout + '.' + choice.language | translate }}</span>
        </button>
      }
      @if (!tailored) {
        <mat-divider></mat-divider>
        <button mat-menu-item type="button" (click)="tailorToPosting()" data-dossier-posting>
          <mat-icon>auto_awesome</mat-icon>
          <span>{{ 'ai.dossier.posting.open' | translate }}</span>
        </button>
      }
    </mat-menu>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class DossierButtonComponent {
  /** The opportunity to tailor the dossier to with AI; the profile's own dossier without one. */
  @Input() projectId: number | null = null;
  /** The feed offer to tailor the dossier to with AI, without adding it to the pipeline. */
  @Input() offerId: number | null = null;
  /** Each dossier the AI tailored, as the file downloaded, e.g. to attach it to the opportunity the offer becomes. */
  @Output() readonly made = new EventEmitter<File>();

  private readonly users = inject(UserManagementService);
  private readonly ai = inject(AiService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);

  busy = false;

  readonly choices: { layout: Layout; language: Language }[] = [
    { layout: 'NAMED', language: 'FR' }, { layout: 'ANONYMOUS', language: 'FR' },
    { layout: 'NAMED', language: 'EN' }, { layout: 'ANONYMOUS', language: 'EN' }
  ];

  /** Tailored with AI, to an opportunity or a feed offer. */
  get tailored(): boolean {
    return !!this.projectId || !!this.offerId;
  }

  download(layout: Layout, language: Language): void {
    const { projectId, offerId } = this;
    const tailored = this.tailored;
    const pdf: Observable<Blob> = projectId ? this.ai.tailoredDossier(projectId, layout, language) :
      offerId ? this.ai.offerDossier(offerId, layout, language) : this.users.downloadDossier(layout, language);
    const name = `${language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}` +
      `${tailored ? '-tailored' : ''}.pdf`;
    this.busy = true;
    pdf.subscribe({
      next: file => {
        this.done();
        saveBlob(file, name);
        if (tailored) {
          this.made.emit(new File([file], name, { type: 'application/pdf' }));
        }
      },
      error: (error: unknown) => {
        this.done();
        if (tailored) {
          void aiBlobErrorKey(error).then(key => this.notifications.error(key));
        } else {
          this.notifications.error('profile.dossier.error');
        }
      }
    });
  }

  tailorToPosting(): void {
    this.dialog.open(PostingDossierDialogComponent, { width: '640px', maxWidth: '95vw' });
  }

  private done(): void {
    this.busy = false;
    this.changes.markForCheck();
  }
}

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { AiService, aiBlobErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { saveBlob } from '../../../shared/utils/download-file';

type Layout = 'NAMED' | 'ANONYMOUS';
type Language = 'FR' | 'EN';

/**
 * Downloads the dossier de compétences (see docs/features.md#dossier-de-competences): named or anonymized, in French
 * or English, built from the profile, the certifications and the won missions. Given an opportunity, the AI tailors it
 * to that opportunity (see docs/features.md#ai-features).
 */
@Component({
  selector: 'app-dossier-button',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <button mat-stroked-button type="button" [matMenuTriggerFor]="dossierMenu" [disabled]="busy"
      [attr.data-dossier]="projectId ? 'tailored' : 'profile'">
      @if (busy) {
        <mat-progress-spinner mode="indeterminate" diameter="18" [attr.aria-label]="'ai.dossier.busy' | translate"></mat-progress-spinner>
      } @else {
        <mat-icon>{{ projectId ? 'auto_awesome' : 'badge' }}</mat-icon>
      }
      {{ (projectId ? 'ai.dossier.open' : 'profile.dossier.download') | translate }}
    </button>
    <mat-menu #dossierMenu="matMenu">
      @for (choice of choices; track choice.layout + choice.language) {
        <button mat-menu-item type="button" (click)="download(choice.layout, choice.language)"
          [attr.data-dossier-choice]="choice.layout + '-' + choice.language">
          <mat-icon>{{ choice.layout === 'NAMED' ? 'person' : 'visibility_off' }}</mat-icon>
          <span>{{ 'profile.dossier.' + choice.layout + '.' + choice.language | translate }}</span>
        </button>
      }
    </mat-menu>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class DossierButtonComponent {
  /** The opportunity to tailor the dossier to with AI; the profile's own dossier without one. */
  @Input() projectId: number | null = null;

  private readonly users = inject(UserManagementService);
  private readonly ai = inject(AiService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  busy = false;

  readonly choices: { layout: Layout; language: Language }[] = [
    { layout: 'NAMED', language: 'FR' }, { layout: 'ANONYMOUS', language: 'FR' },
    { layout: 'NAMED', language: 'EN' }, { layout: 'ANONYMOUS', language: 'EN' }
  ];

  download(layout: Layout, language: Language): void {
    const projectId = this.projectId;
    const pdf: Observable<Blob> = projectId ? this.ai.tailoredDossier(projectId, layout, language) : this.users.downloadDossier(layout, language);
    const name = `${language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}` +
      `${projectId ? '-tailored' : ''}.pdf`;
    this.busy = true;
    pdf.subscribe({
      next: file => {
        this.done();
        saveBlob(file, name);
      },
      error: (error: unknown) => {
        this.done();
        if (projectId) {
          void aiBlobErrorKey(error).then(key => this.notifications.error(key));
        } else {
          this.notifications.error('profile.dossier.error');
        }
      }
    });
  }

  private done(): void {
    this.busy = false;
    this.changes.markForCheck();
  }
}

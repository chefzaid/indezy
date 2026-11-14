import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { saveBlob } from '../../../shared/utils/download-file';

type Layout = 'NAMED' | 'ANONYMOUS';
type Language = 'FR' | 'EN';

/**
 * Downloads the dossier de compétences (see docs/features.md#dossier-de-competences): named or anonymized, in French
 * or English, built from the profile, the certifications and the won missions.
 */
@Component({
  selector: 'app-dossier-button',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, TranslateModule],
  template: `
    <button mat-stroked-button type="button" [matMenuTriggerFor]="dossierMenu" data-dossier>
      <mat-icon>badge</mat-icon>{{ 'profile.dossier.download' | translate }}
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
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);

  readonly choices: { layout: Layout; language: Language }[] = [
    { layout: 'NAMED', language: 'FR' }, { layout: 'ANONYMOUS', language: 'FR' },
    { layout: 'NAMED', language: 'EN' }, { layout: 'ANONYMOUS', language: 'EN' }
  ];

  download(layout: Layout, language: Language): void {
    this.users.downloadDossier(layout, language).subscribe({
      next: pdf => saveBlob(pdf, `${language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}.pdf`),
      error: () => this.notifications.error('profile.dossier.error')
    });
  }
}

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { CvProfile } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { CvProfileDialogComponent } from './cv-profile-dialog.component';

/**
 * The CV profiles (see docs/features.md#cv-profiles): named positionings of the profile ("Java / Cloud backend", "Tech
 * Lead"), each downloaded as its own dossier, one the default the dossier download draws.
 */
@Component({
  selector: 'app-profile-cv-profiles',
  imports: [MatButtonModule, MatDialogModule, MatDividerModule, MatIconModule, MatMenuModule, TranslateModule],
  template: `
    <section class="cv-profiles" aria-labelledby="cv-profiles-title" data-cv-profiles>
      <div class="heading">
        <h2 id="cv-profiles-title">{{ 'cvProfiles.title' | translate }}</h2>
        <button mat-stroked-button type="button" (click)="open()" [disabled]="profiles.length >= max" data-cv-profile-add>
          <mat-icon>add</mat-icon>{{ 'cvProfiles.add' | translate }}
        </button>
      </div>
      <p class="muted">{{ 'cvProfiles.intro' | translate }}</p>
      @for (profile of profiles; track profile.id) {
        <div class="entry" [attr.data-cv-profile]="profile.id">
          <mat-icon class="kind" aria-hidden="true">{{ profile.isDefault ? 'star' : 'badge' }}</mat-icon>
          <div class="text">
            <h3>{{ profile.name }}</h3>
            <span class="muted">{{ (profile.isDefault ? 'cvProfiles.default' : 'cvProfiles.other') | translate:{ language: profile.language } }}</span>
          </div>
          <button mat-icon-button type="button" [matMenuTriggerFor]="menu" [attr.aria-label]="'cvProfiles.actions' | translate:{ name: profile.name }"
            [attr.data-cv-profile-menu]="profile.id">
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #menu="matMenu">
            @for (choice of downloads; track choice.layout + choice.format) {
              <button mat-menu-item type="button" (click)="download(profile, choice.layout, choice.format)"
                [attr.data-cv-profile-download]="choice.layout + '-' + choice.format">
                <mat-icon>{{ choice.format === 'DOCX' ? 'description' : 'picture_as_pdf' }}</mat-icon>
                <span>{{ 'cvProfiles.download.' + choice.layout + '_' + choice.format | translate }}</span>
              </button>
            }
            <mat-divider></mat-divider>
            <button mat-menu-item type="button" (click)="open(profile)" data-cv-profile-edit><mat-icon>edit</mat-icon><span>{{ 'common.edit' | translate }}</span></button>
            @if (!profile.isDefault) {
              <button mat-menu-item type="button" (click)="makeDefault(profile)" data-cv-profile-default>
                <mat-icon>star</mat-icon><span>{{ 'cvProfiles.makeDefault' | translate }}</span>
              </button>
            }
            <button mat-menu-item type="button" (click)="remove(profile)" data-cv-profile-delete><mat-icon>delete</mat-icon><span>{{ 'common.delete' | translate }}</span></button>
          </mat-menu>
        </div>
      } @empty {
        <p class="muted" data-no-cv-profile>{{ 'cvProfiles.none' | translate }}</p>
      }
    </section>
  `,
  styles: [`
    .cv-profiles { margin-top: 24px; }
    .heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
    .heading h2 { margin: 0; font-size: 1.15rem; }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .entry { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .entry .text { flex: 1; min-width: 0; }
    .entry h3 { margin: 0; font-size: 1rem; overflow-wrap: anywhere; }
    .kind { color: var(--mat-sys-primary); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileCvProfilesComponent implements OnInit {
  private readonly service = inject(CvProfileService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly max = 20;
  readonly downloads: { layout: 'NAMED' | 'ANONYMOUS'; format: 'PDF' | 'DOCX' }[] = [
    { layout: 'NAMED', format: 'PDF' }, { layout: 'ANONYMOUS', format: 'PDF' }, { layout: 'NAMED', format: 'DOCX' }, { layout: 'ANONYMOUS', format: 'DOCX' }
  ];
  profiles: CvProfile[] = [];

  ngOnInit(): void {
    this.load();
    this.service.changed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private load(): void {
    this.service.list().subscribe({
      next: profiles => {
        this.profiles = profiles;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('cvProfiles.loadError')
    });
  }

  open(profile?: CvProfile): void {
    this.dialog.open(CvProfileDialogComponent, { data: { profile }, width: '680px', maxWidth: '95vw' }).afterClosed()
      .pipe(filter(saved => saved === true)).subscribe(() => this.load());
  }

  download(profile: CvProfile, layout: 'NAMED' | 'ANONYMOUS', format: 'PDF' | 'DOCX'): void {
    this.service.dossier(profile.id, layout, format).subscribe({
      next: file => saveBlob(file, `${fileWord(profile.name)}${layout === 'ANONYMOUS' ? '-anonymous' : ''}.${format === 'DOCX' ? 'docx' : 'pdf'}`),
      error: () => this.notifications.error('profile.dossier.error')
    });
  }

  makeDefault(profile: CvProfile): void {
    this.service.makeDefault(profile.id).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('cvProfiles.saveError')
    });
  }

  remove(profile: CvProfile): void {
    this.confirm.confirm({ titleKey: 'cvProfiles.removeTitle', messageKey: 'cvProfiles.removeMessage', messageParams: { name: profile.name },
      confirmKey: 'common.delete', danger: true })
      .pipe(filter(Boolean), switchMap(() => this.service.remove(profile.id))).subscribe({
        next: () => this.load(),
        error: () => this.notifications.error('cvProfiles.saveError')
      });
  }
}

/** The profile's name as a file name word: "backend-kafka"; "cv" without one. */
function fileWord(name: string): string {
  const word = name.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  return 'cv-' + (word || 'profile');
}

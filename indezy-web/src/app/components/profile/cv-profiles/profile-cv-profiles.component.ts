import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { CvProfile } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { aiErrorKey } from '../../../services/ai/ai.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { CvProfileDialogComponent } from './cv-profile-dialog.component';
import { CvShareDialogComponent } from './cv-share-dialog.component';

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
        <button mat-stroked-button type="button" (click)="open()" [disabled]="active.length >= max" data-cv-profile-add>
          <mat-icon>add</mat-icon>{{ 'cvProfiles.add' | translate }}
        </button>
      </div>
      <p class="muted">{{ 'cvProfiles.intro' | translate }}</p>
      @for (profile of active; track profile.id) {
        <div class="entry" [class.twin]="isTwinOfPrevious(profile)" [attr.data-cv-profile]="profile.id">
          <mat-icon class="kind" aria-hidden="true">{{ profile.isDefault ? 'star' : 'badge' }}</mat-icon>
          <div class="text">
            <h3>{{ profile.name }}</h3>
            <span class="muted">{{ (profile.isDefault ? 'cvProfiles.default' : 'cvProfiles.other') | translate:{ language: profile.language } }}</span>
            @if (twinOf(profile); as twin) {
              <span class="muted" [attr.data-cv-profile-twin]="profile.id"> · {{ 'cvProfiles.twin' | translate:{ name: twin.name } }}</span>
            }
            @if (profile.changes?.length) {
              <div class="review" role="status" [attr.data-cv-profile-review]="profile.id">
                <p><mat-icon aria-hidden="true">update</mat-icon><strong>{{ 'cvProfiles.toReview' | translate }}</strong></p>
                <ul>
                  @for (change of profile.changes; track $index) {
                    <li>{{ 'cvProfiles.changes.' + change.kind | translate:{ label: change.label ?? '', fields: fields(change.fields) } }}</li>
                  }
                </ul>
                <button mat-button type="button" (click)="open(profile)">{{ 'common.edit' | translate }}</button>
                <button mat-button type="button" (click)="markReviewed(profile)" [attr.data-cv-profile-reviewed]="profile.id">
                  {{ 'cvProfiles.markReviewed' | translate }}
                </button>
              </div>
            }
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
            <button mat-menu-item type="button" (click)="share(profile)" data-cv-profile-share>
              <mat-icon>link</mat-icon><span>{{ 'cvShares.open' | translate }}</span>
            </button>
            <button mat-menu-item type="button" (click)="translate(profile)" [disabled]="translating" data-cv-profile-translate>
              <mat-icon>translate</mat-icon>
              <span>{{ (twinOf(profile) ? 'cvProfiles.retranslate' : 'cvProfiles.translate') | translate:{ language: other(profile) } }}</span>
            </button>
            <button mat-menu-item type="button" (click)="open(profile)" data-cv-profile-edit><mat-icon>edit</mat-icon><span>{{ 'common.edit' | translate }}</span></button>
            <button mat-menu-item type="button" (click)="duplicate(profile)" [disabled]="active.length >= max" data-cv-profile-duplicate>
              <mat-icon>content_copy</mat-icon><span>{{ 'cvProfiles.duplicate' | translate }}</span>
            </button>
            <button mat-menu-item type="button" (click)="archive(profile, true)" data-cv-profile-archive>
              <mat-icon>archive</mat-icon><span>{{ 'cvProfiles.archive' | translate }}</span>
            </button>
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
      @if (archived.length) {
        <button mat-button type="button" (click)="showArchived = !showArchived" [attr.aria-expanded]="showArchived" data-cv-profiles-archived-toggle>
          <mat-icon>{{ showArchived ? 'expand_less' : 'expand_more' }}</mat-icon>{{ 'cvProfiles.archived' | translate:{ count: archived.length } }}
        </button>
        @if (showArchived) {
          @for (profile of archived; track profile.id) {
            <div class="entry archived" [attr.data-cv-profile-archived]="profile.id">
              <mat-icon class="kind" aria-hidden="true">inventory_2</mat-icon>
              <div class="text">
                <h3>{{ profile.name }}</h3>
                <span class="muted">{{ 'cvProfiles.other' | translate:{ language: profile.language } }}</span>
              </div>
              <button mat-button type="button" (click)="archive(profile, false)" [disabled]="active.length >= max"
                [attr.data-cv-profile-restore]="profile.id">{{ 'cvProfiles.restore' | translate }}</button>
              <button mat-icon-button type="button" (click)="remove(profile)" [attr.aria-label]="'cvProfiles.deleteNamed' | translate:{ name: profile.name }">
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        }
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
    .entry.twin { border-top-style: dashed; padding-left: 24px; }
    .entry.archived .kind { color: var(--mat-sys-on-surface-variant); }
    .review { margin-top: 6px; padding: 6px 10px; border-radius: 8px; background: color-mix(in srgb, var(--mat-sys-tertiary) 10%, transparent); }
    .review p { display: flex; align-items: center; gap: 6px; margin: 0; }
    .review mat-icon { width: 18px; height: 18px; font-size: 18px; color: var(--mat-sys-tertiary); }
    .review ul { margin: 4px 0; padding-left: 24px; font-size: .875rem; }
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
  private readonly i18n = inject(TranslateService);

  readonly max = 20;
  readonly downloads: { layout: 'NAMED' | 'ANONYMOUS'; format: 'PDF' | 'DOCX' }[] = [
    { layout: 'NAMED', format: 'PDF' }, { layout: 'ANONYMOUS', format: 'PDF' }, { layout: 'NAMED', format: 'DOCX' }, { layout: 'ANONYMOUS', format: 'DOCX' }
  ];
  profiles: CvProfile[] = [];
  translating = false;
  showArchived = false;

  /** The profiles in use, each twin right after its profile. */
  get active(): CvProfile[] {
    return this.ordered.filter(profile => !profile.archived);
  }

  get archived(): CvProfile[] {
    return this.profiles.filter(profile => profile.archived);
  }

  /** Shares the profile through a link of its own, or revokes one of its links. */
  share(profile: CvProfile): void {
    this.dialog.open(CvShareDialogComponent, { data: { profile }, width: '600px', maxWidth: '95vw' });
  }

  duplicate(profile: CvProfile): void {
    this.service.duplicate(profile.id).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('cvProfiles.saveError')
    });
  }

  /** Archives the profile (kept, out of the default and of the limit) or brings it back. */
  archive(profile: CvProfile, archived: boolean): void {
    this.service.archive(profile.id, archived).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('cvProfiles.saveError')
    });
  }

  /** The profiles in their order, each twin right after the profile it was written from. */
  get ordered(): CvProfile[] {
    const placed: CvProfile[] = [];
    for (const profile of this.profiles) {
      if (!placed.includes(profile)) {
        placed.push(profile);
        const twin = this.twinOf(profile);
        if (twin && !placed.includes(twin)) {
          placed.push(twin);
        }
      }
    }
    return placed;
  }

  twinOf(profile: CvProfile): CvProfile | undefined {
    return profile.twinId ? this.profiles.find(candidate => candidate.id === profile.twinId) : undefined;
  }

  /** Whether the profile is listed right after its twin. */
  isTwinOfPrevious(profile: CvProfile): boolean {
    const ordered = this.ordered;
    const index = ordered.indexOf(profile);
    return index > 0 && ordered[index - 1].id === profile.twinId;
  }

  /** The facts of an item that changed, as words: "dates, stack". */
  fields(fields: string[]): string {
    return fields.map(field => this.i18n.instant('cvProfiles.fields.' + field)).join(', ');
  }

  /** The profile's wording reviewed: in step with the facts again. */
  markReviewed(profile: CvProfile): void {
    this.service.reviewed(profile.id).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('cvProfiles.saveError')
    });
  }

  /** The language a profile's twin is written in. */
  other(profile: CvProfile): string {
    return profile.language === 'EN' ? 'FR' : 'EN';
  }

  /** Writes or rewrites the profile's twin in the other language with AI. */
  translate(profile: CvProfile): void {
    this.translating = true;
    this.service.translate(profile.id).subscribe({
      next: twin => {
        this.translating = false;
        this.notifications.success('cvProfiles.translated', undefined, { name: twin.name });
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.translating = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

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

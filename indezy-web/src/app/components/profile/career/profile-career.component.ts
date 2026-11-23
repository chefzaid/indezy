import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, filter, switchMap } from 'rxjs';
import { Education, EducationDetails, Experience, ExperienceDetails } from '../../../models';
import { CareerService } from '../../../services/career/career.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { EducationDialogComponent } from './education-dialog.component';
import { ExperienceDialogComponent } from './experience-dialog.component';

/**
 * The profile's past positions and schools (see docs/features.md#experience-and-education), printed in the dossier de
 * compétences after the won missions and on the public profile when chosen.
 */
@Component({
  selector: 'app-profile-career',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="career" aria-labelledby="career-positions" data-career>
      <div class="heading">
        <h2 id="career-positions">{{ 'career.positions' | translate }}</h2>
        <button mat-stroked-button type="button" (click)="addExperience()" data-add-experience>
          <mat-icon>add</mat-icon>{{ 'career.addPosition' | translate }}
        </button>
      </div>
      <p class="hint">{{ 'career.hint' | translate }}</p>
      @for (experience of experiences; track experience.id) {
        <mat-card class="entry" [attr.data-experience]="experience.id">
          <mat-icon class="kind" aria-hidden="true">work_history</mat-icon>
          <div class="text">
            <h3>{{ experience.title }} · {{ experience.employer }}</h3>
            <span class="muted">
              @if (experience.startsOn) {
                {{ experience.startsOn | date:'MMM y' }} – {{ experience.endsOn ? (experience.endsOn | date:'MMM y') : ('career.now' | translate) }}
              }
              @if (experience.shownPublicly) {
                · {{ 'career.public' | translate }}
              }
            </span>
            @if (experience.stack) {
              <span class="muted">{{ experience.stack }}</span>
            }
          </div>
          <button mat-icon-button type="button" [matMenuTriggerFor]="positionMenu"
            [attr.aria-label]="'career.actions' | translate:{ name: experience.title }">
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #positionMenu="matMenu">
            <button mat-menu-item type="button" (click)="editExperience(experience)"><mat-icon>edit</mat-icon><span>{{ 'common.edit' | translate }}</span></button>
            <button mat-menu-item type="button" (click)="removeExperience(experience)"><mat-icon>delete</mat-icon><span>{{ 'common.delete' | translate }}</span></button>
          </mat-menu>
        </mat-card>
      } @empty {
        <p class="muted" data-no-experience>{{ 'career.noPosition' | translate }}</p>
      }

      <div class="heading">
        <h2 id="career-schools">{{ 'career.schools' | translate }}</h2>
        <button mat-stroked-button type="button" (click)="addEducation()" data-add-education>
          <mat-icon>add</mat-icon>{{ 'career.addSchool' | translate }}
        </button>
      </div>
      @for (education of educations; track education.id) {
        <mat-card class="entry" [attr.data-education]="education.id">
          <mat-icon class="kind" aria-hidden="true">school</mat-icon>
          <div class="text">
            <h3>{{ education.degree ? education.degree + ' · ' : '' }}{{ education.school }}</h3>
            <span class="muted">
              {{ years(education) }}
              @if (education.shownPublicly) {
                {{ years(education) ? '·' : '' }} {{ 'career.public' | translate }}
              }
            </span>
          </div>
          <button mat-icon-button type="button" [matMenuTriggerFor]="schoolMenu"
            [attr.aria-label]="'career.actions' | translate:{ name: education.school }">
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #schoolMenu="matMenu">
            <button mat-menu-item type="button" (click)="editEducation(education)"><mat-icon>edit</mat-icon><span>{{ 'common.edit' | translate }}</span></button>
            <button mat-menu-item type="button" (click)="removeEducation(education)"><mat-icon>delete</mat-icon><span>{{ 'common.delete' | translate }}</span></button>
          </mat-menu>
        </mat-card>
      } @empty {
        <p class="muted" data-no-education>{{ 'career.noSchool' | translate }}</p>
      }
    </section>
  `,
  styles: [`
    .career { margin-top: 24px; }
    .heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin: 24px 0 8px; }
    .heading h2 { margin: 0; font-size: 1.15rem; }
    .hint, .muted { color: var(--app-text-muted); font-size: 0.9rem; }
    .hint { margin: 0 0 12px; }
    .entry { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; padding: 12px 8px 12px 16px; }
    .kind { flex: none; color: var(--app-link); }
    .text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
    .text h3 { margin: 0; font-size: 1rem; overflow-wrap: anywhere; }
    .text .muted { font-size: 0.85rem; overflow-wrap: anywhere; }
  `]
})
export class ProfileCareerComponent implements OnInit {
  private readonly career = inject(CareerService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  experiences: Experience[] = [];
  educations: Education[] = [];

  ngOnInit(): void {
    this.reload();
    this.career.changed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reload());
  }

  /** Reads both lists again, e.g. once the profile import added to them. */
  reload(): void {
    this.career.experiences().subscribe({
      next: experiences => this.show(() => this.experiences = experiences),
      error: () => this.notifications.error('career.loadError')
    });
    this.career.educations().subscribe({
      next: educations => this.show(() => this.educations = educations),
      error: () => this.notifications.error('career.loadError')
    });
  }

  years(education: Education): string {
    return [education.startYear, education.endYear].filter(year => !!year).join(' – ');
  }

  addExperience(): void {
    this.openExperience(null).pipe(switchMap(details => this.career.createExperience(details))).subscribe({
      next: () => this.reload(),
      error: () => this.notifications.error('career.saveError')
    });
  }

  editExperience(experience: Experience): void {
    this.openExperience(experience).pipe(switchMap(details => this.career.updateExperience(experience.id, details))).subscribe({
      next: () => this.reload(),
      error: () => this.notifications.error('career.saveError')
    });
  }

  removeExperience(experience: Experience): void {
    this.confirmDelete(experience.title).pipe(switchMap(() => this.career.deleteExperience(experience.id))).subscribe({
      next: () => this.show(() => this.experiences = this.experiences.filter(each => each.id !== experience.id)),
      error: () => this.notifications.error('career.deleteError')
    });
  }

  addEducation(): void {
    this.openEducation(null).pipe(switchMap(details => this.career.createEducation(details))).subscribe({
      next: () => this.reload(),
      error: () => this.notifications.error('career.saveError')
    });
  }

  editEducation(education: Education): void {
    this.openEducation(education).pipe(switchMap(details => this.career.updateEducation(education.id, details))).subscribe({
      next: () => this.reload(),
      error: () => this.notifications.error('career.saveError')
    });
  }

  removeEducation(education: Education): void {
    this.confirmDelete(education.school).pipe(switchMap(() => this.career.deleteEducation(education.id))).subscribe({
      next: () => this.show(() => this.educations = this.educations.filter(each => each.id !== education.id)),
      error: () => this.notifications.error('career.deleteError')
    });
  }

  private openExperience(experience: Experience | null): Observable<ExperienceDetails> {
    return this.dialog.open<ExperienceDialogComponent, Experience | null, ExperienceDetails>(ExperienceDialogComponent,
      { data: experience, width: '600px', maxWidth: '95vw', autoFocus: 'first-tabbable' }).afterClosed()
      .pipe(filter((details): details is ExperienceDetails => !!details));
  }

  private openEducation(education: Education | null): Observable<EducationDetails> {
    return this.dialog.open<EducationDialogComponent, Education | null, EducationDetails>(EducationDialogComponent,
      { data: education, width: '520px', maxWidth: '95vw', autoFocus: 'first-tabbable' }).afterClosed()
      .pipe(filter((details): details is EducationDetails => !!details));
  }

  private confirmDelete(name: string): Observable<boolean> {
    return this.confirm.confirm({ titleKey: 'career.deleteTitle', messageKey: 'career.deleteMessage', messageParams: { name },
      confirmKey: 'common.delete', danger: true }).pipe(filter(Boolean));
  }

  private show(change: () => void): void {
    change();
    this.changes.markForCheck();
  }
}

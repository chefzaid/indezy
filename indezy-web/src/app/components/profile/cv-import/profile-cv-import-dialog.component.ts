import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, catchError, first, forkJoin, of, tap, throwError } from 'rxjs';
import { AiImportedCertification, AiImportedPosition, AiImportedSchool, AiProfileImport, Education, Experience, SkillDetail, SkillLevel,
  Training, UserProfile } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { CareerService } from '../../../services/career/career.service';
import { ProfileCvService } from '../../../services/profile-cv/profile-cv.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { SkillNamesService } from '../../../services/skill/skill-names.service';
import { SkillNames, skillKey } from '../../../shared/utils/skill-names';
import { TrainingService } from '../../../services/training/training.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';

/** At most this many skills on a profile, as the skills editor keeps. */
export const CV_IMPORT_SKILLS_MAX = 50;
/** The presentation's length on the profile form. */
const BIO_MAX = 500;

/** What the dialog is opened with: the profile as the form holds it, and the languages the profile offers. */
export interface CvImportData {
  current: Partial<UserProfile>;
  languages: string[];
}

type TextField = 'firstName' | 'lastName' | 'phone' | 'city' | 'position' | 'company' | 'bio' | 'website' | 'linkedin' | 'github';

/** A field the CV gives a value for that differs from the profile's, ticked to be taken. */
export interface CvImportRow {
  field: TextField;
  label: string;
  found: string;
  current: string | null;
  chosen: boolean;
}

/** A certification found, ticked to be added to the training record, or already there. */
export interface CvImportCertification extends AiImportedCertification {
  chosen: boolean;
  here: boolean;
}

/** A position found, ticked to be added to the profile's past positions, or already there. */
export interface CvImportPosition extends AiImportedPosition {
  chosen: boolean;
  here: boolean;
}

/** A school found, ticked to be added to the profile's education, or already there. */
export interface CvImportSchool extends AiImportedSchool {
  chosen: boolean;
  here: boolean;
}

const same = (one: string | null | undefined, other: string | null | undefined): boolean =>
  (one ?? '').trim().toLowerCase() === (other ?? '').trim().toLowerCase();

const FIELDS: { field: TextField; label: string }[] = [
  { field: 'firstName', label: 'contacts.firstName' }, { field: 'lastName', label: 'common.name' },
  { field: 'phone', label: 'contacts.phone' }, { field: 'city', label: 'clients.city' },
  { field: 'position', label: 'contacts.position' }, { field: 'company', label: 'profile.company' },
  { field: 'bio', label: 'profile.bio' }, { field: 'website', label: 'common.website' },
  { field: 'linkedin', label: 'LinkedIn' }, { field: 'github', label: 'GitHub' }
];

/**
 * Fills the profile from a CV with AI (see docs/features.md#ai-features), or from LinkedIn's data archive or profile PDF without AI
 * (docs/features.md#profile-import): the file is read on the server and nothing is kept there; what it says is shown beside what the profile holds, ticked where the profile is empty, and only what is
 * ticked goes into the form, to review and save as usual. Skills and languages the profile lacks are added, never removed.
 * Certifications go to the training record (docs/features.md#training-record) and positions and schools to the profile's
 * past ones (docs/features.md#experience-and-education) when the form is filled, those already there (case aside) marked.
 */
@Component({
  selector: 'app-profile-cv-import-dialog',
  imports: [DatePipe, FormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'profile.cvImport.title' | translate }}</h2>
    <mat-dialog-content>
      @if (!reading) {
        <p class="hint">{{ 'profile.cvImport.hint' | translate }}</p>
        <input #file type="file" accept=".pdf,.docx,.doc,.odt" hidden (change)="read($event)" data-cv-import-file
          [attr.aria-label]="'profile.cvImport.pick' | translate">
        <input #archive type="file" accept=".zip,application/zip,.pdf,application/pdf" hidden (change)="read($event, true)" data-cv-import-linkedin-file
          [attr.aria-label]="'profile.cvImport.pickLinkedIn' | translate">
        <div class="picks">
          <button mat-stroked-button type="button" (click)="file.click()" [disabled]="loading" data-cv-import-pick>
            <mat-icon>upload_file</mat-icon>{{ 'profile.cvImport.pick' | translate }}
          </button>
          <button mat-stroked-button type="button" (click)="archive.click()" [disabled]="loading" data-cv-import-linkedin>
            <mat-icon>folder_zip</mat-icon>{{ 'profile.cvImport.pickLinkedIn' | translate }}
          </button>
        </div>
        <p class="hint">{{ 'profile.cvImport.linkedInHint' | translate }}</p>
        @if (loading) {
          <p class="loading"><mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
            {{ 'profile.cvImport.reading' | translate }}</p>
        }
      } @else {
        @if (!rows.length && !skills.length && !languages.length && !newCertifications && !newPositions && !newSchools) {
          <p data-cv-import-nothing>{{ 'profile.cvImport.nothing' | translate }}</p>
          @if (skillsHere.length) {
            <p class="current" data-cv-import-skills-here>{{ 'profile.cvImport.skillsHere' | translate:{ skills: skillsHere.join(', ') } }}</p>
          }
        } @else {
          <p class="hint">{{ 'profile.cvImport.review' | translate }}</p>
          <ul class="rows">
            @for (row of rows; track row.field) {
              <li>
                <mat-checkbox [(ngModel)]="row.chosen" [attr.data-cv-import-row]="row.field">
                  <strong>{{ row.label | translate }}</strong> {{ row.found }}
                </mat-checkbox>
                @if (row.current) {
                  <span class="current">{{ 'profile.cvImport.current' | translate:{ value: row.current } }}</span>
                }
              </li>
            }
            @if (skills.length && !overLimit) {
              <li>
                <mat-checkbox [(ngModel)]="takeSkills" data-cv-import-skills>
                  <strong>{{ 'profile.cvImport.skills' | translate:{ count: skills.length } }}</strong>
                  {{ skillNames }}
                </mat-checkbox>
              </li>
            }
            @if (overLimit) {
              <li data-cv-import-skills-over>
                <strong>{{ 'profile.cvImport.skillsOver' | translate:{ room: room, count: skills.length, max: maxSkills } }}</strong>
                <span class="choices">
                  @for (skill of skills; track skill.name) {
                    <mat-checkbox [(ngModel)]="skill.chosen" [disabled]="!skill.chosen && chosenSkills >= room"
                      [attr.data-cv-import-skill]="skill.name">{{ skill.name }}</mat-checkbox>
                  }
                </span>
              </li>
            }
            @if (skillsHere.length) {
              <li class="current" data-cv-import-skills-here>{{ 'profile.cvImport.skillsHere' | translate:{ skills: skillsHere.join(', ') } }}</li>
            }
            @if (languages.length) {
              <li>
                <mat-checkbox [(ngModel)]="takeLanguages" data-cv-import-languages>
                  <strong>{{ 'profile.cvImport.languages' | translate }}</strong> {{ languages.join(', ') }}
                </mat-checkbox>
              </li>
            }
            @if (languagesLeftOut.length) {
              <li class="current" data-cv-import-left-out>
                {{ 'profile.cvImport.languagesLeftOut' | translate:{ languages: languagesLeftOut.join(', ') } }}
              </li>
            }
          </ul>
          @if (certifications.length) {
            <h3 class="section">{{ 'profile.cvImport.certifications' | translate }}</h3>
            <p class="hint">{{ 'profile.cvImport.certificationsHint' | translate }}</p>
            <ul class="rows">
              @for (certification of certifications; track certification.name) {
                <li>
                  @if (certification.here) {
                    <span data-cv-import-certification-here>
                      <strong>{{ certification.name }}</strong>{{ certification.authority ? ' · ' + certification.authority : '' }}
                      <span class="current">{{ 'profile.cvImport.alreadyHere' | translate }}</span>
                    </span>
                  } @else {
                    <mat-checkbox [(ngModel)]="certification.chosen" [attr.data-cv-import-certification]="certification.name">
                      <strong>{{ certification.name }}</strong>{{ certification.authority ? ' · ' + certification.authority : '' }}
                      @if (certification.obtainedOn) {
                        {{ 'profile.cvImport.obtained' | translate:{ date: (certification.obtainedOn | date:'MMMM y') } }}
                      }
                    </mat-checkbox>
                  }
                </li>
              }
            </ul>
          }
          @if (positions.length) {
            <h3 class="section">{{ 'career.positions' | translate }}</h3>
            <ul class="rows">
              @for (position of positions; track $index) {
                <li>
                  @if (position.here) {
                    <span data-cv-import-position-here>
                      <strong>{{ position.title }}</strong> · {{ position.employer }}
                      <span class="current">{{ 'profile.cvImport.alreadyInProfile' | translate }}</span>
                    </span>
                  } @else {
                    <mat-checkbox [(ngModel)]="position.chosen" [attr.data-cv-import-position]="position.title + ' · ' + position.employer">
                      <strong>{{ position.title }}</strong> · {{ position.employer }}
                      @if (position.startsOn) {
                        ({{ position.startsOn | date:'MMM y' }} – {{ position.endsOn ? (position.endsOn | date:'MMM y') : ('career.now' | translate) }})
                      }
                    </mat-checkbox>
                  }
                </li>
              }
            </ul>
          }
          @if (schools.length) {
            <h3 class="section">{{ 'career.schools' | translate }}</h3>
            <ul class="rows">
              @for (school of schools; track $index) {
                <li>
                  @if (school.here) {
                    <span data-cv-import-school-here>
                      <strong>{{ school.school }}</strong>{{ school.degree ? ' · ' + school.degree : '' }}
                      <span class="current">{{ 'profile.cvImport.alreadyInProfile' | translate }}</span>
                    </span>
                  } @else {
                    <mat-checkbox [(ngModel)]="school.chosen" [attr.data-cv-import-school]="school.school">
                      <strong>{{ school.school }}</strong>{{ school.degree ? ' · ' + school.degree : '' }}
                      {{ school.endYear ? '(' + (school.startYear ? school.startYear + ' – ' : '') + school.endYear + ')' : '' }}
                    </mat-checkbox>
                  }
                </li>
              }
            </ul>
          }
        }
      }
      @if (reading && cvFile) {
        <mat-checkbox class="keep" [(ngModel)]="keepCv" data-cv-import-keep>{{ 'profile.cvImport.keepCv' | translate:{ name: cvFile.name } }}</mat-checkbox>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      @if (reading) {
        <button mat-flat-button color="primary" type="button" (click)="apply()" [disabled]="!anyChosen || saving" data-cv-import-apply>
          {{ 'profile.cvImport.apply' | translate }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .hint { color: var(--mat-sys-on-surface-variant); }
    .picks { display: flex; flex-wrap: wrap; gap: 8px; }
    .loading { display: flex; align-items: center; gap: 8px; }
    .rows { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
    .rows li { display: flex; flex-direction: column; }
    .section { margin: 16px 0 0; font-size: 1em; }
    .keep { display: block; margin-top: 12px; }
    .choices { display: flex; flex-wrap: wrap; gap: 0 8px; }
    .current { margin-left: 40px; font-size: 0.85em; color: var(--mat-sys-on-surface-variant); }
    mat-spinner { display: inline-block; margin-right: 8px; }
  `]
})
export class ProfileCvImportDialogComponent {
  private readonly ai = inject(AiService);
  private readonly users = inject(UserManagementService);
  private readonly trainings = inject(TrainingService);
  private readonly career = inject(CareerService);
  private readonly cvs = inject(ProfileCvService);
  private readonly skillNamesService = inject(SkillNamesService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<ProfileCvImportDialogComponent, Partial<UserProfile>>>(MatDialogRef);
  private readonly data = inject<CvImportData>(MAT_DIALOG_DATA);
  private readonly changes = inject(ChangeDetectorRef);

  loading = false;
  reading: AiProfileImport | null = null;
  rows: CvImportRow[] = [];
  readonly maxSkills = CV_IMPORT_SKILLS_MAX;
  /** Skills the profile lacks; past the limit, each is chosen on its own, at most {@link room}. */
  skills: { name: string; years: number; level?: SkillLevel | null; chosen: boolean }[] = [];
  /** Skills found that the profile already names, under another spelling or alias too, as the profile writes them. */
  skillsHere: string[] = [];
  room = 0;
  languages: string[] = [];
  /** Languages found that the profile does not offer. */
  languagesLeftOut: string[] = [];
  certifications: CvImportCertification[] = [];
  positions: CvImportPosition[] = [];
  /** The CV read, which can be kept in the profile; none for LinkedIn's files. */
  cvFile: File | null = null;
  keepCv = false;
  schools: CvImportSchool[] = [];
  saving = false;
  takeSkills = true;
  takeLanguages = true;

  get skillNames(): string {
    return this.skills.map(skill => skill.name).join(', ');
  }

  /** More skills found than the profile has room for. */
  get overLimit(): boolean {
    return this.skills.length > this.room;
  }

  get chosenSkills(): number {
    return this.skills.filter(skill => skill.chosen).length;
  }

  /** The skills to add: all when they fit and are ticked, else those chosen. */
  get skillsTaken(): { name: string; years: number; level?: SkillLevel | null }[] {
    return this.overLimit ? this.skills.filter(skill => skill.chosen) : this.takeSkills ? this.skills : [];
  }

  get anyChosen(): boolean {
    return this.rows.some(row => row.chosen) || this.skillsTaken.length > 0 ||
      (this.takeLanguages && this.languages.length > 0) || (this.keepCv && !!this.cvFile) || [...this.certifications, ...this.positions, ...this.schools].some(entry => entry.chosen);
  }

  get newPositions(): number {
    return this.positions.filter(position => !position.here).length;
  }

  get newSchools(): number {
    return this.schools.filter(school => !school.here).length;
  }

  /** How many certifications the training record does not name yet. */
  get newCertifications(): number {
    return this.certifications.filter(certification => !certification.here).length;
  }

  /** Reads a CV with AI, or LinkedIn's data archive without it. */
  read(event: Event, linkedIn = false): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.loading = true;
    this.cvFile = linkedIn ? null : file;
    const known: Observable<Training[]> = this.trainings.list().pipe(catchError(() => of([])));
    const names = this.skillNamesService.names$.pipe(first(), catchError(() => of(SkillNames.NONE)));
    const experiences: Observable<Experience[]> = this.career.experiences().pipe(catchError(() => of([])));
    const educations: Observable<Education[]> = this.career.educations().pipe(catchError(() => of([])));
    let withoutAi = false;
    // Without AI (none set up, the allowance spent, the provider down), the CV is read by its headings instead.
    const source = linkedIn ? this.users.linkedInImport(file) : this.ai.profileImport(file).pipe(catchError((error: HttpErrorResponse) =>
      error.status === 400 ? throwError(() => error) : this.users.cvImport(file).pipe(tap(() => withoutAi = true))));
    forkJoin([source, known, names, experiences, educations]).subscribe({
      next: ([reading, trainings, skillNames, held, studied]) => {
        this.loading = false;
        if (withoutAi) {
          this.notifications.info('profile.cvImport.readWithoutAi');
        }
        this.show(reading, trainings, skillNames, held, studied);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notifications.error(error.status === 400 ? (linkedIn ? 'profile.cvImport.notLinkedIn' : 'profile.cvImport.unreadable') :
          'profile.cvImport.readError');
        this.changes.markForCheck();
      }
    });
  }

  /** Lays out what the CV says beside what the profile holds. */
  show(reading: AiProfileImport, trainings: Training[] = [], names: SkillNames = SkillNames.NONE, experiences: Experience[] = [],
    educations: Education[] = []): void {
    const current = this.data.current;
    this.rows = FIELDS.flatMap(({ field, label }) => {
      let found = reading[field]?.trim() ?? '';
      if (field === 'bio') {
        found = found.slice(0, BIO_MAX);
      }
      const now = (current[field] as string | null | undefined)?.trim() || null;
      return found && found !== now ? [{ field, label, found, current: now, chosen: !now }] : [];
    });
    const key = (skill: string): string => skillKey(names.canonical(skill));
    const listed = new Map((current.skills ?? []).map(skill => [key(skill), skill]));
    const seen = new Set<string>();
    const found = (reading.skills ?? []).filter(skill => skill.name?.trim()).map(skill => ({ name: names.canonical(skill.name), years: skill.years, level: skill.level ?? null }))
      .filter(skill => !seen.has(key(skill.name)) && !!seen.add(key(skill.name)));
    this.skillsHere = found.filter(skill => listed.has(key(skill.name))).map(skill => listed.get(key(skill.name)) as string);
    this.room = Math.max(0, CV_IMPORT_SKILLS_MAX - listed.size);
    this.skills = found.filter(skill => !listed.has(key(skill.name))).map((skill, index) => ({ ...skill, chosen: index < this.room }));
    if (!this.room) {
      this.skills = [];
    }
    const spoken = new Set(current.languages ?? []);
    const languages = [...new Set((reading.languages ?? []).map(language => language.trim()).filter(Boolean))];
    this.languages = languages.filter(language => this.data.languages.includes(language) && !spoken.has(language));
    this.languagesLeftOut = languages.filter(language => !this.data.languages.includes(language));
    const named = new Set(trainings.flatMap(training => [training.certification, training.title])
      .filter((name): name is string => !!name).map(name => name.trim().toLowerCase()));
    this.certifications = (reading.certifications ?? []).filter(certification => certification.name?.trim())
      .map(certification => {
        const here = named.has(certification.name.trim().toLowerCase());
        return { ...certification, name: certification.name.trim(), here, chosen: !here };
      });
    this.positions = (reading.positions ?? []).filter(position => position.employer?.trim() && position.title?.trim()).map(position => {
      const here = experiences.some(held => same(held.employer, position.employer) && same(held.title, position.title));
      return { ...position, here, chosen: !here };
    });
    this.schools = (reading.education ?? []).filter(school => school.school?.trim()).map(school => {
      const here = educations.some(studied => same(studied.school, school.school) && (!studied.degree || !school.degree ||
        same(studied.degree, school.degree)));
      return { ...school, here, chosen: !here };
    });
    this.reading = reading;
  }

  /** Closes with what is ticked, as the profile form's values. */
  apply(): void {
    const patch: Partial<UserProfile> = {};
    for (const row of this.rows.filter(chosen => chosen.chosen)) {
      patch[row.field] = row.found;
    }
    const skills = this.skillsTaken;
    if (skills.length) {
      const details: Record<string, SkillDetail> = { ...this.data.current.skillDetails };
      skills.filter(skill => skill.years > 0 || skill.level).forEach(skill => details[skill.name] = {
        ...(skill.years > 0 ? { years: skill.years } : {}), ...(skill.level ? { level: skill.level } : {}) });
      patch.skills = [...(this.data.current.skills ?? []), ...skills.map(skill => skill.name)];
      patch.skillDetails = details;
    }
    if (this.takeLanguages && this.languages.length) {
      patch.languages = [...(this.data.current.languages ?? []), ...this.languages];
    }
    const additions: Observable<unknown>[] = [
      ...this.certifications.filter(certification => certification.chosen && !certification.here).map(certification => this.trainings.create({
        title: certification.name, provider: certification.authority, certification: certification.name,
        certifiedOn: certification.obtainedOn, certificationExpiresOn: certification.expiresOn
      })),
      ...this.positions.filter(position => position.chosen && !position.here).map(position => this.career.createExperience({
        employer: position.employer.trim(), title: position.title.trim(), startsOn: position.startsOn, endsOn: position.endsOn,
        description: position.description?.slice(0, 2000) ?? null, stack: null, shownPublicly: false
      })),
      ...this.schools.filter(school => school.chosen && !school.here).map(school => this.career.createEducation({
        school: school.school.trim(), degree: school.degree, startYear: school.startYear || null, endYear: school.endYear || null,
        shownPublicly: false
      }))
    ];
    const keeping = this.keepCv && this.cvFile ? this.cvs.keep(this.cvFile) : null;
    if (!additions.length && !keeping) {
      this.dialogRef.close(patch);
      return;
    }
    if (keeping) {
      additions.push(keeping);
    }
    this.saving = true;
    forkJoin(additions).subscribe({
      next: () => {
        this.notifications.success(keeping && additions.length === 1 ? 'profile.cvImport.cvKept' : 'profile.cvImport.recordsAdded', undefined,
          { count: additions.length - (keeping ? 1 : 0) });
        this.career.changed.next();
        this.cvs.changed.next();
        this.dialogRef.close(patch);
      },
      error: () => {
        this.saving = false;
        this.notifications.error('profile.cvImport.recordsError');
        this.career.changed.next();
        this.cvs.changed.next();
        this.changes.markForCheck();
      }
    });
  }
}

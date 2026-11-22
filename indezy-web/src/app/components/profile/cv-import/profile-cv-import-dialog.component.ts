import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AiProfileImport, SkillDetail, UserProfile } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
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

const FIELDS: { field: TextField; label: string }[] = [
  { field: 'firstName', label: 'contacts.firstName' }, { field: 'lastName', label: 'common.name' },
  { field: 'phone', label: 'contacts.phone' }, { field: 'city', label: 'clients.city' },
  { field: 'position', label: 'contacts.position' }, { field: 'company', label: 'profile.company' },
  { field: 'bio', label: 'profile.bio' }, { field: 'website', label: 'common.website' },
  { field: 'linkedin', label: 'LinkedIn' }, { field: 'github', label: 'GitHub' }
];

/**
 * Fills the profile from a CV with AI (see docs/features.md#ai-features), or from LinkedIn's data archive without AI
 * (docs/features.md#profile-import): the file is read on the server and nothing is kept there; what it says is shown beside what the profile holds, ticked where the profile is empty, and only what is
 * ticked goes into the form, to review and save as usual. Skills and languages the profile lacks are added, never removed.
 */
@Component({
  selector: 'app-profile-cv-import-dialog',
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'profile.cvImport.title' | translate }}</h2>
    <mat-dialog-content>
      @if (!reading) {
        <p class="hint">{{ 'profile.cvImport.hint' | translate }}</p>
        <input #file type="file" accept=".pdf,.docx,.odt" hidden (change)="read($event)" data-cv-import-file
          [attr.aria-label]="'profile.cvImport.pick' | translate">
        <input #archive type="file" accept=".zip,application/zip" hidden (change)="read($event, true)" data-cv-import-linkedin-file
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
        @if (!rows.length && !skills.length && !languages.length) {
          <p data-cv-import-nothing>{{ 'profile.cvImport.nothing' | translate }}</p>
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
            @if (skills.length) {
              <li>
                <mat-checkbox [(ngModel)]="takeSkills" data-cv-import-skills>
                  <strong>{{ 'profile.cvImport.skills' | translate:{ count: skills.length } }}</strong>
                  {{ skillNames }}
                </mat-checkbox>
              </li>
            }
            @if (languages.length) {
              <li>
                <mat-checkbox [(ngModel)]="takeLanguages" data-cv-import-languages>
                  <strong>{{ 'profile.cvImport.languages' | translate }}</strong> {{ languages.join(', ') }}
                </mat-checkbox>
              </li>
            }
          </ul>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      @if (reading) {
        <button mat-flat-button color="primary" type="button" (click)="apply()" [disabled]="!anyChosen" data-cv-import-apply>
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
    .current { margin-left: 40px; font-size: 0.85em; color: var(--mat-sys-on-surface-variant); }
    mat-spinner { display: inline-block; margin-right: 8px; }
  `]
})
export class ProfileCvImportDialogComponent {
  private readonly ai = inject(AiService);
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<ProfileCvImportDialogComponent, Partial<UserProfile>>>(MatDialogRef);
  private readonly data = inject<CvImportData>(MAT_DIALOG_DATA);
  private readonly changes = inject(ChangeDetectorRef);

  loading = false;
  reading: AiProfileImport | null = null;
  rows: CvImportRow[] = [];
  skills: { name: string; years: number }[] = [];
  languages: string[] = [];
  takeSkills = true;
  takeLanguages = true;

  get skillNames(): string {
    return this.skills.map(skill => skill.name).join(', ');
  }

  get anyChosen(): boolean {
    return this.rows.some(row => row.chosen) || (this.takeSkills && this.skills.length > 0) ||
      (this.takeLanguages && this.languages.length > 0);
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
    (linkedIn ? this.users.linkedInImport(file) : this.ai.profileImport(file)).subscribe({
      next: reading => {
        this.loading = false;
        this.show(reading);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notifications.error(error.status === 400 ? (linkedIn ? 'profile.cvImport.notLinkedIn' : 'profile.cvImport.unreadable') :
          linkedIn ? 'profile.cvImport.readError' : aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  /** Lays out what the CV says beside what the profile holds. */
  show(reading: AiProfileImport): void {
    const current = this.data.current;
    this.rows = FIELDS.flatMap(({ field, label }) => {
      let found = reading[field]?.trim() ?? '';
      if (field === 'bio') {
        found = found.slice(0, BIO_MAX);
      }
      const now = (current[field] as string | null | undefined)?.trim() || null;
      return found && found !== now ? [{ field, label, found, current: now, chosen: !now }] : [];
    });
    const listed = new Set((current.skills ?? []).map(skill => skill.toLowerCase()));
    const room = Math.max(0, CV_IMPORT_SKILLS_MAX - listed.size);
    this.skills = (reading.skills ?? []).filter(skill => skill.name?.trim() && !listed.has(skill.name.trim().toLowerCase()))
      .map(skill => ({ name: skill.name.trim(), years: skill.years }))
      .slice(0, room);
    const spoken = new Set(current.languages ?? []);
    this.languages = [...new Set((reading.languages ?? []).map(language => language.trim()))]
      .filter(language => this.data.languages.includes(language) && !spoken.has(language));
    this.reading = reading;
  }

  /** Closes with what is ticked, as the profile form's values. */
  apply(): void {
    const patch: Partial<UserProfile> = {};
    for (const row of this.rows.filter(chosen => chosen.chosen)) {
      patch[row.field] = row.found;
    }
    if (this.takeSkills && this.skills.length) {
      const details: Record<string, SkillDetail> = { ...this.data.current.skillDetails };
      this.skills.filter(skill => skill.years > 0).forEach(skill => details[skill.name] = { years: skill.years });
      patch.skills = [...(this.data.current.skills ?? []), ...this.skills.map(skill => skill.name)];
      patch.skillDetails = details;
    }
    if (this.takeLanguages && this.languages.length) {
      patch.languages = [...(this.data.current.languages ?? []), ...this.languages];
    }
    this.dialogRef.close(patch);
  }
}

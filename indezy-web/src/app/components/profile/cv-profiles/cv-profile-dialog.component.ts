import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { CvProfile, CvProfileDetails, CvProfileItem, CvProfileSource, CvProfileSources, DocumentLanguage } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** A skill, mission or position a profile shows or not, in its place, with its own wording for a mission or position. */
export interface PickRow {
  key: string;
  label: string;
  shown: boolean;
  /** A mission's or position's id; none for a skill. */
  id?: number;
  /** The profile's own description, as a placeholder. */
  source?: string | null;
  text?: string;
}

/**
 * Writes a CV profile (see docs/features.md#cv-profiles): its name and language, its headline and summary (the
 * profile's when left empty), which skills, won missions and past positions it shows, in what order, and their wording.
 */
@Component({
  selector: 'app-cv-profile-dialog',
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ (profile ? 'cvProfiles.editTitle' : 'cvProfiles.newTitle') | translate }}</h2>
    <mat-dialog-content>
      @if (!sources) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'common.loading' | translate"></mat-spinner></div>
      } @else {
        <p class="hint">{{ 'cvProfiles.hint' | translate }}</p>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>{{ 'cvProfiles.name' | translate }}</mat-label>
          <input matInput [(ngModel)]="name" maxlength="80" required [placeholder]="'cvProfiles.nameHint' | translate" data-cv-profile-name>
        </mat-form-field>
        <mat-button-toggle-group [(ngModel)]="language" [attr.aria-label]="'ai.cv.language' | translate" data-cv-profile-language>
          <mat-button-toggle value="FR">Français</mat-button-toggle>
          <mat-button-toggle value="EN">English</mat-button-toggle>
        </mat-button-toggle-group>
        <mat-form-field appearance="outline" class="wide gap">
          <mat-label>{{ 'ai.cv.headline' | translate }}</mat-label>
          <input matInput [(ngModel)]="headline" maxlength="120" [placeholder]="sources.headline ?? ''" data-cv-profile-headline>
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>{{ 'ai.cv.summary' | translate }}</mat-label>
          <textarea matInput rows="4" [(ngModel)]="summary" maxlength="900" [placeholder]="sources.summary ?? ''" data-cv-profile-summary></textarea>
        </mat-form-field>
        @for (group of groups; track group.key) {
          @if (group.rows.length) {
            <section class="group" [attr.aria-labelledby]="'cv-profile-' + group.key" [attr.data-cv-profile-group]="group.key">
              <h3 [id]="'cv-profile-' + group.key">{{ group.title | translate }}</h3>
              @for (row of group.rows; track row.key; let index = $index, last = $last) {
                <div class="row" [attr.data-cv-profile-row]="row.key">
                  <mat-checkbox [(ngModel)]="row.shown">{{ row.label }}</mat-checkbox>
                  <span class="spacer"></span>
                  <button mat-icon-button type="button" (click)="move(group.rows, index, -1)" [disabled]="index === 0"
                    [attr.aria-label]="'cvProfiles.up' | translate:{ name: row.label }"><mat-icon>arrow_upward</mat-icon></button>
                  <button mat-icon-button type="button" (click)="move(group.rows, index, 1)" [disabled]="last"
                    [attr.aria-label]="'cvProfiles.down' | translate:{ name: row.label }"><mat-icon>arrow_downward</mat-icon></button>
                </div>
                @if (row.id !== undefined && row.shown) {
                  <mat-form-field appearance="outline" class="wide wording" subscriptSizing="dynamic">
                    <mat-label>{{ 'cvProfiles.wording' | translate }}</mat-label>
                    <textarea matInput rows="2" [(ngModel)]="row.text" maxlength="2000" [placeholder]="row.source ?? ''"
                      [attr.data-cv-profile-text]="row.key"></textarea>
                  </mat-form-field>
                }
              }
            </section>
          }
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="!sources || !name.trim() || saving" data-cv-profile-save>
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    .hint { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); }
    .wide { width: 100%; }
    .gap { margin-top: 12px; }
    .group { border-top: 1px solid var(--mat-sys-outline-variant); padding-top: 8px; margin-top: 8px; }
    .group h3 { margin: 0 0 4px; font-size: 1rem; }
    .row { display: flex; align-items: center; }
    .spacer { flex: 1; }
    .wording { margin: 0 0 8px 40px; width: calc(100% - 40px); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CvProfileDialogComponent {
  private readonly profiles = inject(CvProfileService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialogRef = inject<MatDialogRef<CvProfileDialogComponent, boolean>>(MatDialogRef);
  readonly profile = inject<{ profile?: CvProfile } | null>(MAT_DIALOG_DATA, { optional: true })?.profile ?? null;

  sources: CvProfileSources | null = null;
  name = this.profile?.name ?? '';
  language: DocumentLanguage = this.profile?.language ?? 'FR';
  headline = this.profile?.headline ?? '';
  summary = this.profile?.summary ?? '';
  groups: { key: 'skills' | 'missions' | 'positions'; title: string; rows: PickRow[] }[] = [];
  saving = false;

  constructor() {
    this.profiles.sources().subscribe({
      next: sources => {
        this.show(sources);
        this.changes.markForCheck();
      },
      error: () => {
        this.notifications.error('cvProfiles.loadError');
        this.dialogRef.close(false);
      }
    });
  }

  /** The profile's choices first, in its order, then what it leaves out, unticked; a profile without choices shows all. */
  private show(sources: CvProfileSources): void {
    this.sources = sources;
    const skills = ordered(sources.skills.map(skill => ({ key: 'skill-' + skill, label: skill, shown: true })),
      this.profile?.skills?.map(skill => 'skill-' + skill));
    const items = (kind: string, list: CvProfileSource[], chosen: CvProfileItem[] | null | undefined): PickRow[] => {
      const texts = new Map((chosen ?? []).map(item => [item.id, item.text ?? '']));
      return ordered(list.map(source => ({ key: `${kind}-${source.id}`, label: source.title ?? '—', shown: true, id: source.id,
        source: source.description, text: texts.get(source.id) ?? '' })), chosen?.map(item => `${kind}-${item.id}`));
    };
    this.groups = [
      { key: 'skills', title: 'cvProfiles.skills', rows: skills },
      { key: 'missions', title: 'cvProfiles.missions', rows: items('mission', sources.missions, this.profile?.missions) },
      { key: 'positions', title: 'cvProfiles.positions', rows: items('position', sources.positions, this.profile?.positions) }
    ];
  }

  move(rows: PickRow[], index: number, by: -1 | 1): void {
    const target = index + by;
    if (target < 0 || target >= rows.length) {
      return;
    }
    [rows[index], rows[target]] = [rows[target], rows[index]];
  }

  /** What the profile is set to: a list as the profile has it (all shown, in its order, unworded) is left null. */
  details(): CvProfileDetails {
    const rows = (key: string): PickRow[] => this.groups.find(group => group.key === key)?.rows ?? [];
    const asIs = (list: PickRow[], order: string[]): boolean => list.every(row => row.shown && !(row.text ?? '').trim()) &&
      list.map(row => row.key).join() === order.join();
    const sources = this.sources!;
    const items = (kind: string, list: CvProfileSource[]): CvProfileItem[] | null => {
      const shown = rows(kind + 's');
      return asIs(shown, list.map(source => `${kind}-${source.id}`)) ? null :
        shown.filter(row => row.shown).map(row => ({ id: row.id!, text: (row.text ?? '').trim() || null }));
    };
    const skills = rows('skills');
    return {
      name: this.name.trim(),
      language: this.language,
      headline: this.headline.trim() || null,
      summary: this.summary.trim() || null,
      skills: asIs(skills, sources.skills.map(skill => 'skill-' + skill)) ? null : skills.filter(row => row.shown).map(row => row.label),
      missions: items('mission', sources.missions),
      positions: items('position', sources.positions)
    };
  }

  save(): void {
    if (!this.sources || !this.name.trim() || this.saving) {
      return;
    }
    this.saving = true;
    const details = this.details();
    (this.profile ? this.profiles.update(this.profile.id, details) : this.profiles.create(details)).subscribe({
      next: () => {
        this.notifications.success('cvProfiles.saved');
        this.dialogRef.close(true);
      },
      error: () => {
        this.saving = false;
        this.notifications.error('cvProfiles.saveError');
        this.changes.markForCheck();
      }
    });
  }
}

/** The rows in the chosen keys' order, ticked, then the others unticked; without a choice, all as they are. */
function ordered(rows: PickRow[], chosen: string[] | undefined): PickRow[] {
  if (!chosen) {
    return rows;
  }
  const byKey = new Map(rows.map(row => [row.key, row]));
  const picked = chosen.map(key => byKey.get(key)).filter((row): row is PickRow => !!row);
  return [...picked, ...rows.filter(row => !chosen.includes(row.key)).map(row => ({ ...row, shown: false }))];
}

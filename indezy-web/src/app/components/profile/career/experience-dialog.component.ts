import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Experience, ExperienceDetails } from '../../../models';

/** "2019-06" of a date, for a month field; empty without one. */
const month = (date: string | null | undefined): string => date ? date.slice(0, 7) : '';

/** Adds a past position or changes it: where, what, from which month to which, and what it was about. */
@Component({
  selector: 'app-experience-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ (experience ? 'career.editPosition' : 'career.addPosition') | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="fields">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.title' | translate }}</mat-label>
          <input matInput formControlName="title" maxlength="200" required data-experience-title>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.employer' | translate }}</mat-label>
          <input matInput formControlName="employer" maxlength="200" required data-experience-employer>
        </mat-form-field>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'career.fields.startsOn' | translate }}</mat-label>
            <input matInput type="month" formControlName="startsOn" data-experience-start>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'career.fields.endsOn' | translate }}</mat-label>
            <input matInput type="month" formControlName="endsOn" data-experience-end>
            <mat-hint>{{ 'career.runningHint' | translate }}</mat-hint>
          </mat-form-field>
        </div>
        @if (form.hasError('endsBeforeStart')) {
          <p class="error" role="alert">{{ 'career.endsBeforeStart' | translate }}</p>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.description' | translate }}</mat-label>
          <textarea matInput formControlName="description" rows="4" maxlength="2000"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.stack' | translate }}</mat-label>
          <input matInput formControlName="stack" maxlength="500">
        </mat-form-field>
        <mat-checkbox formControlName="shownPublicly" data-experience-public>{{ 'career.shownPublicly' | translate }}</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
        <button mat-flat-button color="primary" type="submit" data-experience-save>{{ 'common.save' | translate }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .fields { display: flex; flex-direction: column; gap: 4px; }
    .row { display: flex; flex-wrap: wrap; gap: 0 12px; }
    .row mat-form-field { flex: 1 1 180px; }
    .error { color: var(--app-danger); margin: 0 0 8px; }
    [subscriptsizing="dynamic"] { margin-bottom: 8px; }
  `]
})
export class ExperienceDialogComponent {
  /** The position to change; none to add one. */
  readonly experience = inject<Experience | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ExperienceDialogComponent, ExperienceDetails>);

  readonly form = inject(FormBuilder).nonNullable.group({
    title: [this.experience?.title ?? '', [Validators.required, Validators.maxLength(200)]],
    employer: [this.experience?.employer ?? '', [Validators.required, Validators.maxLength(200)]],
    startsOn: [month(this.experience?.startsOn)],
    endsOn: [month(this.experience?.endsOn)],
    description: [this.experience?.description ?? '', Validators.maxLength(2000)],
    stack: [this.experience?.stack ?? '', Validators.maxLength(500)],
    shownPublicly: [this.experience?.shownPublicly ?? false]
  }, { validators: control => ExperienceDialogComponent.endsAfterStart(control) });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const text = (input: string): string | null => input.trim() || null;
    this.dialogRef.close({
      title: value.title.trim(), employer: value.employer.trim(),
      startsOn: value.startsOn ? `${value.startsOn}-01` : null, endsOn: value.endsOn ? `${value.endsOn}-01` : null,
      description: text(value.description), stack: text(value.stack), shownPublicly: value.shownPublicly
    });
  }

  /** A position ends in or after the month it starts. */
  private static endsAfterStart(control: AbstractControl): ValidationErrors | null {
    const { startsOn, endsOn } = control.value;
    return startsOn && endsOn && endsOn < startsOn ? { endsBeforeStart: true } : null;
  }
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Education, EducationDetails } from '../../../models';

const YEAR = [Validators.min(1950), Validators.max(2100), Validators.pattern(/^\d{4}$/)];

/** Adds a school or degree, or changes it. */
@Component({
  selector: 'app-education-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ (education ? 'career.editSchool' : 'career.addSchool') | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="fields">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.school' | translate }}</mat-label>
          <input matInput formControlName="school" maxlength="200" required data-education-school>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'career.fields.degree' | translate }}</mat-label>
          <input matInput formControlName="degree" maxlength="200" data-education-degree>
        </mat-form-field>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'career.fields.startYear' | translate }}</mat-label>
            <input matInput type="number" min="1950" max="2100" formControlName="startYear" data-education-start>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'career.fields.endYear' | translate }}</mat-label>
            <input matInput type="number" min="1950" max="2100" formControlName="endYear" data-education-end>
          </mat-form-field>
        </div>
        @if (form.hasError('endsBeforeStart')) {
          <p class="error" role="alert">{{ 'career.endsBeforeStart' | translate }}</p>
        }
        <mat-checkbox formControlName="shownPublicly" data-education-public>{{ 'career.shownPublicly' | translate }}</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
        <button mat-flat-button color="primary" type="submit" data-education-save>{{ 'common.save' | translate }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .fields { display: flex; flex-direction: column; gap: 4px; }
    .row { display: flex; flex-wrap: wrap; gap: 0 12px; }
    .row mat-form-field { flex: 1 1 140px; }
    .error { color: var(--app-danger); margin: 0 0 8px; }
  `]
})
export class EducationDialogComponent {
  /** The school to change; none to add one. */
  readonly education = inject<Education | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EducationDialogComponent, EducationDetails>);

  readonly form = inject(FormBuilder).group({
    school: [this.education?.school ?? '', [Validators.required, Validators.maxLength(200)]],
    degree: [this.education?.degree ?? '', Validators.maxLength(200)],
    startYear: [this.education?.startYear ?? null as number | null, YEAR],
    endYear: [this.education?.endYear ?? null as number | null, YEAR],
    shownPublicly: [this.education?.shownPublicly ?? false]
  }, { validators: control => EducationDialogComponent.endsAfterStart(control) });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const year = (input: number | null | undefined): number | null => input === null || input === undefined || `${input}` === '' ? null : Number(input);
    this.dialogRef.close({
      school: value.school!.trim(), degree: value.degree?.trim() || null, startYear: year(value.startYear), endYear: year(value.endYear),
      shownPublicly: !!value.shownPublicly
    });
  }

  /** A school ends in or after the year it starts. */
  private static endsAfterStart(control: AbstractControl): ValidationErrors | null {
    const { startYear, endYear } = control.value;
    return startYear && endYear && Number(endYear) < Number(startYear) ? { endsBeforeStart: true } : null;
  }
}

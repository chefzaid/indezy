import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { SourceService } from '../../../services/source/source.service';
import { AuthService } from '../../../services/auth/auth.service';
import { SourceType } from '../../../models/source.models';

@Component({
    selector: 'app-source-form',
    imports: [
        CommonModule,
        RouterModule,
        ReactiveFormsModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatInputModule,
        MatFormFieldModule,
        MatSelectModule,
        MatCheckboxModule,
        MatProgressSpinnerModule,
        TranslateModule
    ],
    templateUrl: './source-form.component.html',
    styleUrls: ['./source-form.component.scss']
})
export class SourceFormComponent implements OnInit, OnDestroy {
  sourceForm: FormGroup;
  isLoading = false;
  isSubmitting = false;
  isEditMode = false;
  sourceId?: number;

  sourceTypes: { value: SourceType; labelKey: string; icon: string }[] = [
    { value: 'JOB_BOARD', labelKey: 'sources.types.jobBoard', icon: 'work' },
    { value: 'SOCIAL_MEDIA', labelKey: 'sources.types.socialMedia', icon: 'share' },
    { value: 'EMAIL', labelKey: 'sources.types.email', icon: 'email' },
    { value: 'CALL', labelKey: 'sources.types.call', icon: 'phone' },
    { value: 'SMS', labelKey: 'sources.types.sms', icon: 'sms' },
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {
    this.sourceForm = this.createForm();
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.sourceId = +params['id'];
        this.isEditMode = true;
        this.loadSource();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      type: ['JOB_BOARD', [Validators.required]],
      link: [''],
      isListing: [false],
      popularityRating: [null, [Validators.min(1), Validators.max(5)]],
      usefulnessRating: [null, [Validators.min(1), Validators.max(5)]],
      notes: [''],
    });
  }

  private loadSource(): void {
    if (!this.sourceId) { return; }

    this.isLoading = true;
    this.sourceService.getById(this.sourceId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (source) => {
          this.sourceForm.patchValue({
            name: source.name,
            type: source.type,
            link: source.link,
            isListing: source.isListing,
            popularityRating: source.popularityRating,
            usefulnessRating: source.usefulnessRating,
            notes: source.notes,
          });
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading source:', error);
          this.snackBar.open(this.translate.instant('errors.loadingSource'), this.translate.instant('common.close'), { duration: 3000 });
          this.isLoading = false;
          this.router.navigate(['/sources']);
        }
      });
  }

  onSubmit(): void {
    if (this.sourceForm.valid) {
      this.isSubmitting = true;
      const formValue = this.sourceForm.value;

      const freelanceId = this.authService.getUser()?.id;
      if (!freelanceId) {
        this.snackBar.open(this.translate.instant('errors.freelanceNotFound'), this.translate.instant('common.close'), { duration: 3000 });
        this.isSubmitting = false;
        return;
      }

      const sourceData = { ...formValue, freelanceId };

      const operation = this.isEditMode && this.sourceId
        ? this.sourceService.update(this.sourceId, sourceData)
        : this.sourceService.create(sourceData);

      operation.pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          const message = this.isEditMode ? this.translate.instant('sources.updateSuccess') : this.translate.instant('sources.createSuccess');
          this.snackBar.open(message, this.translate.instant('common.close'), { duration: 3000 });
          this.router.navigate(['/sources']);
        },
        error: (error) => {
          console.error('Error saving source:', error);
          const message = this.isEditMode ? this.translate.instant('errors.updatingSource') : this.translate.instant('errors.creatingSource');
          this.snackBar.open(message, this.translate.instant('common.close'), { duration: 3000 });
          this.isSubmitting = false;
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  onCancel(): void {
    this.router.navigate(['/sources']);
  }

  private markFormGroupTouched(): void {
    Object.keys(this.sourceForm.controls).forEach(key => {
      this.sourceForm.get(key)?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string {
    const control = this.sourceForm.get(fieldName);
    if (control?.errors && control.touched) {
      if (control.errors['required']) { return this.translate.instant('errors.fieldRequired'); }
      if (control.errors['minlength']) { return this.translate.instant('errors.minLength', { length: control.errors['minlength'].requiredLength }); }
      if (control.errors['min']) { return this.translate.instant('errors.minValue', { value: 1 }); }
      if (control.errors['max']) { return this.translate.instant('errors.maxValue', { value: 5 }); }
    }
    return '';
  }

  get pageTitle(): string {
    return this.isEditMode ? this.translate.instant('sources.editSource') : this.translate.instant('sources.newSource');
  }

  get submitButtonText(): string {
    return this.isEditMode ? this.translate.instant('common.edit') : this.translate.instant('common.create');
  }
}

import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ClientService } from '../../../services/client/client.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientDto, CreateClientDto } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';

@Component({
    selector: 'app-client-form',
    imports: [
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    TranslateModule
],
    templateUrl: './client-form.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./client-form.component.scss']
})
export class ClientFormComponent implements OnInit, OnDestroy {
  clientForm: FormGroup;
  isEditMode = false;
  isLoading = false;
  isSaving = false;
  clientId?: number;
  private loadedClient?: ClientDto;

  ratingOptions = [1, 2, 3, 4, 5];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly clientService: ClientService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {
    this.clientForm = this.createForm();
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.clientId = +params['id'];
        this.isEditMode = true;
        this.loadClient();
      }
    });

    // Creating an intermediary straight from a link such as /clients/create?type=esn
    if (this.route.snapshot.queryParamMap.get('type') === 'esn') {
      this.clientForm.patchValue({ isFinal: false });
    }

    this.clientForm.get('isBlacklisted')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(blacklisted => {
        if (!blacklisted) {
          this.clientForm.get('blacklistReason')?.setValue('', { emitEvent: false });
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      companyName: ['', [Validators.required, Validators.minLength(2)]],
      city: ['', [Validators.required]],
      address: [''],
      domain: [''],
      notes: [''],
      isFinal: [true, [Validators.required]],
      rating: [null],
      isBlacklisted: [false],
      blacklistReason: ['']
    });
  }

  private loadClient(): void {
    if (!this.clientId) {
      return;
    }

    this.isLoading = true;
    this.clientService.getClient(this.clientId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (client) => {
          if (client) {
            this.loadedClient = client;
            this.clientForm.patchValue({
              companyName: client.companyName,
              city: client.city,
              address: client.address,
              domain: client.domain,
              notes: client.notes,
              isFinal: client.isFinal !== false,
              rating: client.rating ?? null,
              isBlacklisted: !!client.isBlacklisted,
              blacklistReason: client.blacklistReason ?? ''
            });
          } else {
            this.notificationService.error('errors.clientNotFound');
            this.router.navigate(['/clients']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading client:', error);
          this.notificationService.error('errors.loadingClient');
          this.isLoading = false;
        }
      });
  }

  onSubmit(): void {
    if (this.clientForm.invalid || this.isSaving) {
      this.markFormGroupTouched();
      return;
    }
    const freelanceId = this.loadedClient?.freelanceId ?? this.authService.getUser()?.id;
    if (!freelanceId) {
      this.notificationService.error(this.isEditMode ? 'errors.updatingClient' : 'errors.creatingClient');
      return;
    }

    this.isSaving = true;
    const formValue = this.clientForm.value;
    const clientData: CreateClientDto = {
      companyName: formValue.companyName.trim(),
      city: formValue.city.trim(),
      address: formValue.address?.trim() || undefined,
      domain: formValue.domain?.trim() || undefined,
      notes: formValue.notes?.trim() || undefined,
      isFinal: !!formValue.isFinal,
      rating: formValue.rating ?? undefined,
      isBlacklisted: !!formValue.isBlacklisted,
      blacklistReason: formValue.isBlacklisted ? formValue.blacklistReason?.trim() || undefined : undefined,
      freelanceId
    };

    const request = this.isEditMode && this.clientId
      ? this.clientService.update(this.clientId, { ...clientData, id: this.clientId })
      : this.clientService.create(clientData);

    request.pipe(takeUntil(this.destroy$)).subscribe({
      next: (saved) => {
        this.notificationService.success(this.isEditMode ? 'clients.updateSuccess' : 'clients.createSuccess');
        const savedId = saved?.id ?? this.clientId;
        this.router.navigate(savedId ? ['/clients', savedId] : ['/clients']);
      },
      error: (error) => {
        console.error('Error saving client:', error);
        this.notificationService.error(this.isEditMode ? 'errors.updatingClient' : 'errors.creatingClient');
        this.isSaving = false;
      }
    });
  }

  onCancel(): void {
    this.router.navigate(this.isEditMode && this.clientId ? ['/clients', this.clientId] : ['/clients']);
  }

  private markFormGroupTouched(): void {
    Object.keys(this.clientForm.controls).forEach(key => {
      const control = this.clientForm.get(key);
      control?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string {
    const control = this.clientForm.get(fieldName);
    if (control?.errors && control.touched) {
      if (control.errors['required']) {
        return this.translate.instant('errors.fieldRequired');
      }
      if (control.errors['minlength']) {
        return this.translate.instant('errors.minLength', { length: control.errors['minlength'].requiredLength });
      }
    }
    return '';
  }
}

import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { CreateClientDto, UpdateClientDto, ContactDto } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';

@Component({
    selector: 'app-client-form',
    imports: [
        CommonModule,
        RouterModule,
        ReactiveFormsModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatButtonModule,
        MatIconModule,
        MatProgressSpinnerModule,
        MatTabsModule,
        MatTableModule,
        MatMenuModule,
        MatDialogModule,
        MatChipsModule,
        MatDividerModule,
        MatSlideToggleModule,
        TranslateModule
    ],
    templateUrl: './client-form.component.html',
    styleUrls: ['./client-form.component.scss']
})
export class ClientFormComponent implements OnInit, OnDestroy {
  clientForm: FormGroup;
  isEditMode = false;
  isLoading = false;
  isSaving = false;
  clientId?: number;

  // Contact management properties
  contacts: ContactDto[] = [];
  isLoadingContacts = false;
  displayedContactColumns: string[] = ['name', 'email', 'phone', 'position', 'status', 'actions'];
  
  industries = [
    'Technology',
    'Finance',
    'Healthcare',
    'Education',
    'E-commerce',
    'Manufacturing',
    'Consulting',
    'Marketing',
    'Real Estate',
    'Retail',
    'Transportation',
    'Energy',
    'Media',
    'Government',
    'Non-profit',
    'Other'
  ];
  
  statuses = [
    { value: 'ACTIVE', labelKey: 'common.active' },
    { value: 'INACTIVE', labelKey: 'common.inactive' },
    { value: 'ESN', labelKey: 'clients.esn' }
  ];

  ratingOptions = [1, 2, 3, 4, 5];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly clientService: ClientService,
    private readonly contactService: ContactService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly dialog: MatDialog,
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
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      companyName: ['', [Validators.required, Validators.minLength(2)]],
      city: [''],
      address: [''],
      domain: [''],
      notes: [''],
      isFinal: [false, [Validators.required]],
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
            this.clientForm.patchValue(client);
            this.loadContacts(); // Load contacts when editing
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

  private loadContacts(): void {
    if (!this.clientId) {
      return;
    }

    this.isLoadingContacts = true;
    this.contactService.getContactsByClient(this.clientId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contacts) => {
          this.contacts = contacts;
          this.isLoadingContacts = false;
        },
        error: (error) => {
          console.error('Error loading contacts:', error);
          this.notificationService.error('errors.loadingContacts');
          this.isLoadingContacts = false;
        }
      });
  }

  onSubmit(): void {
    if (this.clientForm.valid) {
      this.isSaving = true;
      const formValue = this.clientForm.value;
      
      if (this.isEditMode && this.clientId) {
        const updateData: UpdateClientDto = {
          id: this.clientId,
          ...formValue
        };
        
        this.clientService.updateClient(updateData)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.notificationService.success('clients.updateSuccess');
              this.router.navigate(['/clients']);
            },
            error: (error) => {
              console.error('Error updating client:', error);
              this.notificationService.error('errors.updatingClient');
              this.isSaving = false;
            }
          });
      } else {
        const createData: CreateClientDto = formValue;
        
        this.clientService.createClient(createData)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.notificationService.success('clients.createSuccess');
              this.router.navigate(['/clients']);
            },
            error: (error) => {
              console.error('Error creating client:', error);
              this.notificationService.error('errors.creatingClient');
              this.isSaving = false;
            }
          });
      }
    } else {
      this.markFormGroupTouched();
    }
  }

  onCancel(): void {
    this.router.navigate(['/clients']);
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
      if (control.errors['email']) {
        return this.translate.instant('errors.invalidEmail');
      }
      if (control.errors['minlength']) {
        return this.translate.instant('errors.minLength', { length: control.errors['minlength'].requiredLength });
      }
    }
    return '';
  }

  // Contact management methods
  async onViewContact(contact: ContactDto): Promise<void> {
    const { ContactViewDialogComponent } = await import('../../contacts/contact-view-dialog/contact-view-dialog.component');

    const dialogRef = this.dialog.open(ContactViewDialogComponent, {
      width: '900px',
      maxWidth: '95vw',
      height: '700px',
      maxHeight: '90vh',
      data: contact
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === 'refresh') {
        this.loadContacts();
      }
    });
  }

  onAddContact(): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'contacts', 'create']);
    }
  }

  onEditContact(contact: ContactDto): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'contacts', contact.id, 'edit']);
    }
  }

  onDeleteContact(contact: ContactDto): void {
    if (!contact.id) {
      this.notificationService.error('errors.missingContactId');
      return;
    }

    if (confirm(this.translate.instant('contacts.confirmDelete', { name: `${contact.firstName} ${contact.lastName}` }))) {
      this.contactService.deleteContact(contact.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.success('contacts.deleteSuccess');
            this.loadContacts();
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.notificationService.error('errors.deletingContact');
          }
        });
    }
  }

  getContactStatusColor(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'primary';
      case 'INACTIVE':
        return 'warn';
      default:
        return '';
    }
  }

  getContactStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return this.translate.instant('common.active');
      case 'INACTIVE':
        return this.translate.instant('common.inactive');
      default:
        return status;
    }
  }

  sendContactEmail(contact: ContactDto): void {
    if (contact.email) {
      globalThis.location.href = `mailto:${contact.email}`;
    }
  }

  callContactPhone(contact: ContactDto): void {
    if (contact.phone) {
      globalThis.location.href = `tel:${contact.phone}`;
    }
  }
}

import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';

import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ContactService } from '../../../services/contact/contact.service';
import { ClientService } from '../../../services/client/client.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientDto, ContactDto, CONTACT_LEGAL_BASES, contactName } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { fieldError } from '../../../shared/utils/form-errors';
import { trimmedEmail } from '../../../shared/utils/trimmed-email';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
    selector: 'app-contact-form',
    imports: [PageHeaderComponent, 
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    TranslateModule
],
    templateUrl: './contact-form.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./contact-form.component.scss']
})
export class ContactFormComponent implements OnInit, OnDestroy {
  contactForm: FormGroup;
  clients: ClientDto[] = [];
  /** The other contacts of the workspace, whom this one may report to. */
  managers: ContactDto[] = [];
  isLoading = false;
  isSubmitting = false;
  isEditMode = false;
  readonly legalBases = CONTACT_LEGAL_BASES;
  contactId?: number;
  /** Set when the form is opened from a client's page (/clients/:id/contacts/...). */
  parentClientId?: number;
  private loadedContact?: ContactDto;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly contactService: ContactService,
    private readonly clientService: ClientService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {
    this.contactForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadClients();
    this.loadManagers();

    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      // Under /clients/:id/contacts/... the id parameter is the owning client.
      if (params['id']) {
        this.parentClientId = +params['id'];
        this.contactForm.patchValue({ clientId: this.parentClientId });
      }
      if (params['contactId']) {
        this.contactId = +params['contactId'];
        this.isEditMode = true;
        this.loadContact();
      }
    });

    // Pre-select a client passed as ?clientId=... (e.g. from the contacts list filter).
    const queryClientId = this.route.snapshot.queryParamMap.get('clientId');
    if (queryClientId && !this.isEditMode && !this.parentClientId) {
      this.contactForm.patchValue({ clientId: +queryClientId });
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required]],
      email: ['', [trimmedEmail]],
      phone: [''],
      clientId: [null, [Validators.required]],
      managerId: [null],
      notes: [''],
      legalBasis: ['LEGITIMATE_INTEREST']
    });
  }

  private loadClients(): void {
    this.clientService.getForCurrentFreelance()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = [...clients].sort((a, b) => a.companyName.localeCompare(b.companyName));
        },
        error: (error) => {
          console.error('Error loading clients:', error);
          this.notificationService.error('errors.loadingClients');
        }
      });
  }

  private loadManagers(): void {
    this.contactService.getForCurrentFreelance()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: contacts => this.managers = [...contacts].sort((a, b) => this.nameOf(a).localeCompare(this.nameOf(b))),
        // Reporting lines are optional: the form works without them.
        error: () => this.managers = []
      });
  }

  /** The contacts this one may report to: anyone but itself. */
  get managerOptions(): ContactDto[] {
    return this.managers.filter(contact => contact.id !== this.contactId);
  }

  readonly nameOf = contactName;

  private loadContact(): void {
    if (!this.contactId) { return; }
    
    this.isLoading = true;
    this.contactService.getById(this.contactId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contact) => {
          if (contact) {
            this.loadedContact = contact;
            this.contactForm.patchValue({
              firstName: contact.firstName,
              lastName: contact.lastName,
              email: contact.email,
              phone: contact.phone,
              clientId: contact.clientId,
              managerId: contact.managerId ?? null,
              notes: contact.notes,
              legalBasis: contact.legalBasis ?? 'LEGITIMATE_INTEREST'
            });
          } else {
            this.notificationService.error('errors.contactNotFound');
            this.router.navigate(['/contacts']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading contact:', error);
          this.notificationService.error(error?.status === 404 ? 'errors.contactNotFound' : 'errors.loadingContact');
          this.isLoading = false;
          // Nothing to show or edit: go back to the list instead of an empty page.
          this.router.navigate(['/contacts']);
        }
      });
  }

  onSubmit(): void {
    if (this.contactForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formValue = this.contactForm.value;
      const contactData: ContactDto = {
        ...(this.loadedContact ?? {}),
        firstName: formValue.firstName.trim(),
        lastName: formValue.lastName?.trim() ?? '',
        // Null, not undefined: a field emptied is sent, and cleared.
        email: formValue.email?.trim() || null,
        phone: formValue.phone?.trim() || null,
        notes: formValue.notes?.trim() || null,
        legalBasis: formValue.legalBasis,
        clientId: formValue.clientId,
        managerId: formValue.managerId ?? null,
        freelanceId: this.loadedContact?.freelanceId ?? this.authService.getUser()?.id
      };

      const operation = this.isEditMode
        ? this.contactService.update(this.contactId!, contactData)
        : this.contactService.create(contactData);

      operation.pipe(takeUntil(this.destroy$)).subscribe({
        next: (saved) => {
          this.notificationService.success(this.isEditMode ? 'contacts.updateSuccess' : 'contacts.createSuccess');
          this.navigateAway(saved?.id ?? this.contactId);
        },
        error: (error) => {
          console.error('Error saving contact:', error);
          this.notificationService.error(error?.error?.validationErrors?.managerId ? 'contacts.network.managerLoop'
            : this.isEditMode ? 'errors.updatingContact' : 'errors.creatingContact');
          this.isSubmitting = false;
        }
      });
    } else {
      this.contactForm.markAllAsTouched();
    }
  }

  onCancel(): void {
    this.navigateAway(this.isEditMode ? this.contactId : undefined);
  }

  /** Returns to the client page when opened from it, otherwise to the contact (or the list). */
  private navigateAway(contactId?: number): void {
    if (this.parentClientId) {
      this.router.navigate(['/clients', this.parentClientId], { queryParams: { tab: 'contacts' } });
    } else if (contactId) {
      this.router.navigate(['/contacts', contactId]);
    } else {
      this.router.navigate(['/contacts']);
    }
  }

  getFieldError(fieldName: string): string {
    return fieldError(this.contactForm.get(fieldName), this.translate);
  }

  get pageTitle(): string {
    return this.isEditMode ? this.translate.instant('contacts.editContact') : this.translate.instant('contacts.newContact');
  }

  get submitButtonText(): string {
    return this.isEditMode ? this.translate.instant('common.edit') : this.translate.instant('common.create');
  }
}

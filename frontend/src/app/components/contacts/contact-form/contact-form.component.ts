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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';

import { ContactService, ContactDto } from '../../../services/contact.service';
import { ClientService, ClientDto } from '../../../services/client.service';

@Component({
  selector: 'app-contact-form',
  standalone: true,
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
    MatProgressSpinnerModule,

  ],
  templateUrl: './contact-form.component.html',
  styleUrls: ['./contact-form.component.scss']
})
export class ContactFormComponent implements OnInit, OnDestroy {
  contactForm: FormGroup;
  clients: ClientDto[] = [];
  isLoading = false;
  isSubmitting = false;
  isEditMode = false;
  contactId?: number;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private contactService: ContactService,
    private clientService: ClientService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.contactForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadClients();

    // Handle route parameters
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      // Check if we have a contactId (edit mode)
      if (params['contactId']) {
        this.contactId = +params['contactId'];
        this.isEditMode = true;
        this.loadContact();
      }

      // Get clientId from route params (for both create and edit modes)
      if (params['id']) {
        const clientId = +params['id'];
        this.contactForm.patchValue({ clientId });
      }
    });

    // Handle query parameters for pre-selecting client (fallback)
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(queryParams => {
      if (queryParams['clientId'] && !this.isEditMode) {
        const clientId = +queryParams['clientId'];
        this.contactForm.patchValue({ clientId });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required]],
      position: ['', [Validators.required]],
      clientId: ['', [Validators.required]],
      notes: [''],
      status: ['ACTIVE', [Validators.required]]
    });
  }

  private loadClients(): void {
    this.clientService.getClients()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = clients.filter(client => client.status === 'ACTIVE');
        },
        error: (error) => {
          console.error('Error loading clients:', error);
          this.snackBar.open('Erreur lors du chargement des clients', 'Fermer', { duration: 3000 });
        }
      });
  }

  private loadContact(): void {
    if (!this.contactId) return;
    
    this.isLoading = true;
    this.contactService.getContact(this.contactId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contact) => {
          if (contact) {
            this.contactForm.patchValue({
              firstName: contact.firstName,
              lastName: contact.lastName,
              email: contact.email,
              phone: contact.phone,
              position: contact.position,
              clientId: contact.clientId,
              notes: contact.notes,
              status: contact.status
            });
          } else {
            this.snackBar.open('Contact non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/contacts']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading contact:', error);
          this.snackBar.open('Erreur lors du chargement du contact', 'Fermer', { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  onSubmit(): void {
    if (this.contactForm.valid) {
      this.isSubmitting = true;
      const formValue = this.contactForm.value;
      
      // Find client name for the contact
      const selectedClient = this.clients.find(client => client.id === formValue.clientId);
      const contactData = {
        ...formValue,
        clientName: selectedClient?.name || ''
      };

      const operation = this.isEditMode
        ? this.contactService.updateContact(this.contactId!, contactData)
        : this.contactService.createContact(contactData);

      operation.pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          const message = this.isEditMode ? 'Contact modifié avec succès' : 'Contact créé avec succès';
          this.snackBar.open(message, 'Fermer', { duration: 3000 });

          // Get clientId from form or route params
          const clientId = formValue.clientId || this.route.snapshot.params['id'];
          if (clientId) {
            // Redirect back to client detail page
            this.router.navigate(['/clients', clientId]);
          } else {
            // Default redirect to clients list
            this.router.navigate(['/clients']);
          }
        },
        error: (error) => {
          console.error('Error saving contact:', error);
          const message = this.isEditMode ? 'Erreur lors de la modification du contact' : 'Erreur lors de la création du contact';
          this.snackBar.open(message, 'Fermer', { duration: 3000 });
          this.isSubmitting = false;
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  onCancel(): void {
    // Get clientId from form or route params
    const clientId = this.contactForm.get('clientId')?.value || this.route.snapshot.params['id'];
    if (clientId) {
      // Redirect back to client detail page
      this.router.navigate(['/clients', clientId]);
    } else {
      // Default redirect to clients list
      this.router.navigate(['/clients']);
    }
  }

  private markFormGroupTouched(): void {
    Object.keys(this.contactForm.controls).forEach(key => {
      const control = this.contactForm.get(key);
      control?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string {
    const control = this.contactForm.get(fieldName);
    if (control?.errors && control.touched) {
      if (control.errors['required']) {
        return 'Ce champ est requis';
      }
      if (control.errors['email']) {
        return 'Format d\'email invalide';
      }
      if (control.errors['minlength']) {
        return `Minimum ${control.errors['minlength'].requiredLength} caractères`;
      }
    }
    return '';
  }

  get pageTitle(): string {
    return this.isEditMode ? 'Modifier le Contact' : 'Nouveau Contact';
  }

  get submitButtonText(): string {
    return this.isEditMode ? 'Modifier' : 'Créer';
  }
}

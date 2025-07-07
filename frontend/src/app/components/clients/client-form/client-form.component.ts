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
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';

import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { CreateClientDto, UpdateClientDto, ContactDto } from '../../../models';

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
        MatSlideToggleModule
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
    { value: 'ACTIVE', label: 'Actif' },
    { value: 'INACTIVE', label: 'Inactif' },
    { value: 'ESN', label: 'ESN' }
  ];
  
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private clientService: ClientService,
    private contactService: ContactService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
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
      isFinal: [false, [Validators.required]]
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
            this.snackBar.open('Client non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/clients']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading client:', error);
          this.snackBar.open('Erreur lors du chargement du client', 'Fermer', { duration: 3000 });
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
          this.snackBar.open('Erreur lors du chargement des contacts', 'Fermer', { duration: 3000 });
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
              this.snackBar.open('Client modifié avec succès', 'Fermer', { duration: 3000 });
              this.router.navigate(['/clients']);
            },
            error: (error) => {
              console.error('Error updating client:', error);
              this.snackBar.open('Erreur lors de la modification du client', 'Fermer', { duration: 3000 });
              this.isSaving = false;
            }
          });
      } else {
        const createData: CreateClientDto = formValue;
        
        this.clientService.createClient(createData)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.snackBar.open('Client créé avec succès', 'Fermer', { duration: 3000 });
              this.router.navigate(['/clients']);
            },
            error: (error) => {
              console.error('Error creating client:', error);
              this.snackBar.open('Erreur lors de la création du client', 'Fermer', { duration: 3000 });
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
        return 'Ce champ est requis';
      }
      if (control.errors['email']) {
        return 'Veuillez saisir une adresse email valide';
      }
      if (control.errors['minlength']) {
        return `Minimum ${control.errors['minlength'].requiredLength} caractères`;
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
      this.snackBar.open('Erreur: ID du contact manquant', 'Fermer', { duration: 3000 });
      return;
    }

    if (confirm(`Êtes-vous sûr de vouloir supprimer le contact "${contact.firstName} ${contact.lastName}" ?`)) {
      this.contactService.deleteContact(contact.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Contact supprimé avec succès', 'Fermer', { duration: 3000 });
            this.loadContacts();
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.snackBar.open('Erreur lors de la suppression du contact', 'Fermer', { duration: 3000 });
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
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      default:
        return status;
    }
  }

  sendContactEmail(contact: ContactDto): void {
    if (contact.email) {
      window.location.href = `mailto:${contact.email}`;
    }
  }

  callContactPhone(contact: ContactDto): void {
    if (contact.phone) {
      window.location.href = `tel:${contact.phone}`;
    }
  }
}

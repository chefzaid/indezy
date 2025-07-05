import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { Subject, takeUntil } from 'rxjs';

import { ClientService, ClientDto } from '../../../services/client.service';
import { ContactService, ContactDto } from '../../../services/contact.service';


@Component({
    selector: 'app-client-detail',
    imports: [
        CommonModule,
        RouterModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatChipsModule,
        MatDividerModule,
        MatProgressSpinnerModule,
        MatTableModule,
        MatTabsModule,
        MatMenuModule,
        MatDialogModule
    ],
    templateUrl: './client-detail.component.html',
    styleUrls: ['./client-detail.component.scss']
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  client?: ClientDto;
  contacts: ContactDto[] = [];
  isLoading = false;
  isLoadingContacts = false;
  clientId?: number;
  selectedTabIndex = 0;

  // Contact table columns
  contactDisplayedColumns: string[] = ['name', 'email', 'phone', 'position', 'status', 'actions'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly clientService: ClientService,
    private readonly contactService: ContactService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit(): void {
    console.log('ClientDetailComponent ngOnInit called');
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      console.log('Route params received:', params);
      if (params['id']) {
        this.clientId = +params['id'];
        console.log('Client ID set to:', this.clientId);
        this.loadClientAndContacts();
      } else {
        console.log('No ID parameter found in route');
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadClientAndContacts(): void {
    console.log('loadClientAndContacts called with clientId:', this.clientId);
    if (!this.clientId) {
      console.log('No clientId, returning early');
      return;
    }

    console.log('Setting loading states to true');
    this.isLoading = true;
    this.isLoadingContacts = true;

    // Try loading just the client first to isolate the issue
    console.log('Loading client only first...');
    this.clientService.getClient(this.clientId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (client) => {
          console.log('Client service returned:', client);
          if (client) {
            this.client = client;
            console.log('Client set successfully, now loading contacts...');

            // Now load contacts
            this.contactService.getContactsByClient(this.clientId!)
              .pipe(takeUntil(this.destroy$))
              .subscribe({
                next: (contacts) => {
                  console.log('Contacts loaded:', contacts);
                  this.contacts = contacts;
                  this.isLoading = false;
                  this.isLoadingContacts = false;
                  console.log('All data loaded successfully');
                },
                error: (contactError) => {
                  console.error('Error loading contacts:', contactError);
                  this.isLoading = false;
                  this.isLoadingContacts = false;
                }
              });
          } else {
            console.log('Client not found, showing error and navigating back');
            this.snackBar.open('Client non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/clients']);
            this.isLoading = false;
            this.isLoadingContacts = false;
          }
        },
        error: (error) => {
          console.error('Error loading client:', error);
          this.snackBar.open('Erreur lors du chargement des données', 'Fermer', { duration: 3000 });
          this.isLoading = false;
          this.isLoadingContacts = false;
        }
      });
  }

  private loadContacts(): void {
    if (!this.clientId) return;

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

  onEdit(): void {
    if (this.client) {
      this.router.navigate(['/clients', this.client.id, 'edit']);
    }
  }

  onDelete(): void {
    if (!this.client) return;
    
    if (confirm(`Êtes-vous sûr de vouloir supprimer le client "${this.client.name}" ?`)) {
      this.clientService.deleteClient(this.client.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Client supprimé avec succès', 'Fermer', { duration: 3000 });
            this.router.navigate(['/clients']);
          },
          error: (error) => {
            console.error('Error deleting client:', error);
            this.snackBar.open('Erreur lors de la suppression du client', 'Fermer', { duration: 3000 });
          }
        });
    }
  }

  onBack(): void {
    this.router.navigate(['/clients']);
  }

  getStatusColor(status?: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'primary';
      case 'INACTIVE':
        return 'warn';
      case 'PROSPECT':
        return 'accent';
      default:
        return '';
    }
  }

  getStatusLabel(status?: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      case 'PROSPECT':
        return 'Prospect';
      default:
        return status || 'N/A';
    }
  }

  formatDate(date?: Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString('fr-FR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  openWebsite(): void {
    if (this.client?.website) {
      window.open(this.client.website, '_blank');
    }
  }



  // Contact management methods
  onAddContact(): void {
    if (!this.clientId) return;

    this.router.navigate(['/clients', this.clientId, 'contacts', 'create']);
  }

  onEditContact(contact: ContactDto): void {
    if (!this.clientId) return;
    this.router.navigate(['/clients', this.clientId, 'contacts', contact.id, 'edit']);
  }

  async onViewContact(contact: ContactDto): Promise<void> {
    try {
      // Dynamic import to avoid circular dependency issues
      const { ContactViewDialogComponent } = await import('../../contacts/contact-view-dialog/contact-view-dialog.component');

      const dialogRef = this.dialog.open(ContactViewDialogComponent, {
        width: '900px',
        maxWidth: '95vw',
        height: '700px',
        maxHeight: '90vh',
        data: contact
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result === 'edit') {
          this.onEditContact(contact);
        }
      });
    } catch (error) {
      console.error('Error loading contact view dialog:', error);
      // Fallback: navigate to contact detail page
      this.router.navigate(['/contacts', contact.id]);
    }
  }

  onDeleteContact(contact: ContactDto): void {
    if (confirm(`Êtes-vous sûr de vouloir supprimer le contact "${contact.firstName} ${contact.lastName}" ?`)) {
      this.contactService.deleteContact(contact.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Contact supprimé avec succès', 'Fermer', { duration: 3000 });
            this.loadClientAndContacts(); // Reload client and contacts
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

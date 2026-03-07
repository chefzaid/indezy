import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ContactService } from '../../../services/contact/contact.service';
import { ClientService } from '../../../services/client/client.service';
import { ContactDto, ClientDto } from '../../../models';

@Component({
    selector: 'app-contact-list',
    imports: [
        CommonModule,
        RouterModule,
        FormsModule,
        MatTableModule,
        MatButtonModule,
        MatIconModule,
        MatInputModule,
        MatFormFieldModule,
        MatSelectModule,
        MatChipsModule,
        MatCardModule,
        MatProgressSpinnerModule,
        MatDialogModule,
        MatTooltipModule,
        TranslateModule
    ],
    templateUrl: './contact-list.component.html',
    styleUrls: ['./contact-list.component.scss']
})
export class ContactListComponent implements OnInit, OnDestroy {
  contacts: ContactDto[] = [];
  filteredContacts: ContactDto[] = [];
  clients: ClientDto[] = [];
  isLoading = false;
  searchQuery = '';
  selectedStatus = '';
  selectedClient = '';

  displayedColumns: string[] = ['name', 'email', 'phone', 'position', 'client', 'status', 'actions'];

  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  constructor(
    private readonly contactService: ContactService,
    private readonly clientService: ClientService,
    private readonly router: Router,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.performSearch(query);
    });
  }

  ngOnInit(): void {
    this.loadContacts();
    this.loadClients();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadContacts(): void {
    this.isLoading = true;
    this.contactService.getContacts()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contacts) => {
          this.contacts = contacts;
          this.filteredContacts = [...contacts];
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading contacts:', error);
          this.snackBar.open(this.translate.instant('errors.loadingContacts'), this.translate.instant('common.close'), { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  private loadClients(): void {
    this.clientService.getClients()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = clients;
        },
        error: (error) => {
          console.error('Error loading clients:', error);
        }
      });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    this.searchSubject.next(query);
  }

  private performSearch(query: string): void {
    if (!query.trim()) {
      this.applyFilters();
      return;
    }

    this.contactService.searchContacts(query)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contacts) => {
          this.filteredContacts = contacts;
          this.applyStatusAndClientFilters();
        },
        error: (error) => {
          console.error('Error searching contacts:', error);
        }
      });
  }

  onStatusFilterChange(): void {
    this.applyFilters();
  }

  onClientFilterChange(): void {
    this.applyFilters();
  }

  private applyFilters(): void {
    let filtered = [...this.contacts];

    // Apply search filter
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(contact =>
        contact.firstName.toLowerCase().includes(query) ||
        contact.lastName.toLowerCase().includes(query) ||
        contact.email.toLowerCase().includes(query) ||
        contact.position.toLowerCase().includes(query) ||
        contact.clientName?.toLowerCase().includes(query)
      );
    }

    this.filteredContacts = filtered;
    this.applyStatusAndClientFilters();
  }

  private applyStatusAndClientFilters(): void {
    let filtered = [...this.filteredContacts];

    if (this.selectedStatus) {
      filtered = filtered.filter(contact => contact.status === this.selectedStatus);
    }

    if (this.selectedClient) {
      const clientId = Number.parseInt(this.selectedClient);
      filtered = filtered.filter(contact => contact.clientId === clientId);
    }

    this.filteredContacts = filtered;
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.selectedStatus = '';
    this.selectedClient = '';
    this.filteredContacts = [...this.contacts];
  }

  onCreate(): void {
    this.router.navigate(['/contacts/create']);
  }

  onView(contact: ContactDto): void {
    this.router.navigate(['/contacts', contact.id]);
  }

  onEdit(contact: ContactDto): void {
    this.router.navigate(['/contacts', contact.id, 'edit']);
  }

  onDelete(contact: ContactDto): void {
    if (!contact.id) {
      this.snackBar.open(this.translate.instant('errors.missingContactId'), this.translate.instant('common.close'), { duration: 3000 });
      return;
    }

    if (confirm(this.translate.instant('contacts.confirmDelete', { name: `${contact.firstName} ${contact.lastName}` }))) {
      this.contactService.deleteContact(contact.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open(this.translate.instant('contacts.deleteSuccess'), this.translate.instant('common.close'), { duration: 3000 });
            this.loadContacts();
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.snackBar.open(this.translate.instant('errors.deletingContact'), this.translate.instant('common.close'), { duration: 3000 });
          }
        });
    }
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'primary';
      case 'INACTIVE':
        return 'warn';
      default:
        return '';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return this.translate.instant('common.active');
      case 'INACTIVE':
        return this.translate.instant('common.inactive');
      default:
        return status;
    }
  }

  getFullName(contact: ContactDto): string {
    return `${contact.firstName} ${contact.lastName}`;
  }
}

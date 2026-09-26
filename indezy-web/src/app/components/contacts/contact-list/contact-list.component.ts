import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';

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
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ContactService } from '../../../services/contact/contact.service';
import { ClientService } from '../../../services/client/client.service';
import { ContactDto, ClientDto } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

@Component({
    selector: 'app-contact-list',
    imports: [
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
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./contact-list.component.scss']
})
export class ContactListComponent implements OnInit, OnDestroy {
  contacts: ContactDto[] = [];
  filteredContacts: ContactDto[] = [];
  clients: ClientDto[] = [];
  isLoading = false;
  searchQuery = '';
  selectedClient: number | '' = '';

  displayedColumns: string[] = ['name', 'email', 'phone', 'client', 'actions'];

  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  constructor(
    private readonly contactService: ContactService,
    private readonly clientService: ClientService,
    private readonly router: Router,
    private readonly dialog: MatDialog,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly confirmDialog: ConfirmDialogService
  ) {
    this.searchSubject.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => this.applyFilters());
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
    this.contactService.getForCurrentFreelance()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contacts) => {
          this.contacts = [...contacts].sort((a, b) => this.getFullName(a).localeCompare(this.getFullName(b)));
          this.isLoading = false;
          this.applyFilters();
        },
        error: (error) => {
          console.error('Error loading contacts:', error);
          this.notificationService.error('errors.loadingContacts');
          this.isLoading = false;
        }
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
        }
      });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    this.searchSubject.next(query);
  }

  onClientFilterChange(): void {
    this.applyFilters();
  }

  private applyFilters(): void {
    const query = this.searchQuery.trim().toLowerCase();
    this.filteredContacts = this.contacts.filter(contact => {
      if (this.selectedClient !== '' && contact.clientId !== this.selectedClient) {
        return false;
      }
      if (!query) {
        return true;
      }
      return [contact.firstName, contact.lastName, contact.email, contact.phone, contact.clientName]
        .some(value => value?.toLowerCase().includes(query));
    });
  }

  hasActiveFilters(): boolean {
    return !!this.searchQuery.trim() || this.selectedClient !== '';
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.selectedClient = '';
    this.applyFilters();
  }

  onCreate(): void {
    const clientId = this.selectedClient;
    this.router.navigate(['/contacts/new'], clientId !== '' ? { queryParams: { clientId } } : {});
  }

  onView(contact: ContactDto): void {
    this.router.navigate(['/contacts', contact.id]);
  }

  onEdit(contact: ContactDto): void {
    this.router.navigate(['/contacts', contact.id, 'edit']);
  }

  onDelete(contact: ContactDto): void {
    if (!contact.id) {
      this.notificationService.error('errors.missingContactId');
      return;
    }

    const contactId = contact.id;
    this.confirmDialog.confirm({
      messageKey: 'contacts.confirmDelete',
      messageParams: { name: this.getFullName(contact) },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.contactService.delete(contactId)
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
    });
  }

  getFullName(contact: ContactDto): string {
    return [contact.firstName, contact.lastName].filter(Boolean).join(' ');
  }
}

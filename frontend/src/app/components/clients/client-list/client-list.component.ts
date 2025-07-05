import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';

import { ClientService, ClientDto } from '../../../services/client.service';
import { ContactService } from '../../../services/contact.service';

@Component({
  selector: 'app-client-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDialogModule,

    MatProgressSpinnerModule
  ],
  templateUrl: './client-list.component.html',
  styleUrls: ['./client-list.component.scss']
})
export class ClientListComponent implements OnInit, OnDestroy {
  clients: ClientDto[] = [];
  filteredClients: ClientDto[] = [];
  displayedColumns: string[] = ['name', 'industry', 'status', 'actions'];
  
  searchQuery = '';
  statusFilter = 'ALL';
  searchType = 'CLIENT'; // 'CLIENT' or 'CONTACT'
  isLoading = false;
  
  private destroy$ = new Subject<void>();
  private searchSubject = new Subject<string>();

  constructor(
    private clientService: ClientService,
    private contactService: ContactService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {
    // Setup search debouncing
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.performSearch(query);
    });
  }

  ngOnInit(): void {
    this.loadClients();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadClients(): void {
    this.isLoading = true;
    this.clientService.getClients()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = clients;
          this.applyFilters();
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading clients:', error);
          this.snackBar.open('Erreur lors du chargement des clients', 'Fermer', {
            duration: 3000
          });
          this.isLoading = false;
        }
      });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    this.searchSubject.next(query);
  }

  onStatusFilterChange(): void {
    this.applyFilters();
  }

  onSearchTypeChange(): void {
    // Re-perform search when search type changes
    if (this.searchQuery.trim()) {
      this.performSearch(this.searchQuery);
    }
  }

  private performSearch(query: string): void {
    if (query.trim()) {
      if (this.searchType === 'CLIENT') {
        // Search by client name/info
        this.clientService.searchClients(query)
          .pipe(takeUntil(this.destroy$))
          .subscribe(clients => {
            this.filteredClients = this.filterByStatus(clients);
          });
      } else {
        // Search by contact name/info and return associated clients
        this.contactService.searchContacts(query)
          .pipe(takeUntil(this.destroy$))
          .subscribe(contacts => {
            // Get unique client IDs from matching contacts
            const clientIds = [...new Set(contacts.map(contact => contact.clientId))];

            // Filter clients to only show those with matching contacts
            const matchingClients = this.clients.filter(client =>
              clientIds.includes(client.id)
            );

            this.filteredClients = this.filterByStatus(matchingClients);
          });
      }
    } else {
      this.applyFilters();
    }
  }

  private applyFilters(): void {
    this.filteredClients = this.filterByStatus(this.clients);
  }

  private filterByStatus(clients: ClientDto[]): ClientDto[] {
    if (this.statusFilter === 'ALL') {
      return clients;
    }
    return clients.filter(client => client.status === this.statusFilter);
  }

  deleteClient(client: ClientDto): void {
    if (confirm(`Êtes-vous sûr de vouloir supprimer le client "${client.name}" ?`)) {
      this.clientService.deleteClient(client.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Client supprimé avec succès', 'Fermer', {
              duration: 3000
            });
            this.loadClients();
          },
          error: (error) => {
            console.error('Error deleting client:', error);
            this.snackBar.open('Erreur lors de la suppression du client', 'Fermer', {
              duration: 3000
            });
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
      case 'PROSPECT':
        return 'accent';
      default:
        return '';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      case 'PROSPECT':
        return 'Prospect';
      default:
        return status;
    }
  }
}

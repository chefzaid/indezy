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
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatSliderModule } from '@angular/material/slider';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';

import { ClientService } from '../../../services/client/client.service';
import { ClientDto } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';

@Component({
    selector: 'app-client-list',
    imports: [
        CommonModule,
        RouterModule,
        FormsModule,
        ReactiveFormsModule,
        MatTableModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        MatChipsModule,
        MatInputModule,
        MatFormFieldModule,
        MatSelectModule,
        MatDialogModule,
        MatProgressSpinnerModule,
        MatExpansionModule,
        MatDividerModule,
        MatSliderModule,
        MatCheckboxModule,
        MatDatepickerModule,
        MatNativeDateModule,
        LoadingComponent
    ],
    templateUrl: './client-list.component.html',
    styleUrls: ['./client-list.component.scss']
})
export class ClientListComponent implements OnInit, OnDestroy {
  clients: ClientDto[] = [];
  filteredClients: ClientDto[] = [];
  displayedColumns: string[] = ['name', 'city', 'status', 'actions'];

  // Legacy filters (keeping for compatibility)
  searchQuery = '';
  statusFilter = 'ALL';
  searchType = 'CLIENT'; // 'CLIENT' or 'CONTACT'
  isLoading = false;

  // Advanced filters
  filterForm: FormGroup;
  showAdvancedFilters = false;

  private destroy$ = new Subject<void>();
  private searchSubject = new Subject<string>();

  // Filter options
  industryOptions = [
    'Technologie', 'Finance', 'Santé', 'Éducation', 'Commerce', 'Industrie',
    'Services', 'Immobilier', 'Transport', 'Énergie', 'Média', 'Autre'
  ];

  statusOptions = [
    { value: 'ACTIVE', label: 'Actif' },
    { value: 'INACTIVE', label: 'Inactif' },
    { value: 'PROSPECT', label: 'Prospect' },
    { value: 'ARCHIVED', label: 'Archivé' }
  ];

  projectCountRanges = [
    { value: '0', label: 'Aucun projet' },
    { value: '1-5', label: '1-5 projets' },
    { value: '6-10', label: '6-10 projets' },
    { value: '11-20', label: '11-20 projets' },
    { value: '20+', label: '20+ projets' }
  ];

  sortOptions = [
    { value: 'name', label: 'Nom' },
    { value: 'industry', label: 'Secteur' },
    { value: 'createdAt', label: 'Date de création' },
    { value: 'lastContact', label: 'Dernier contact' },
    { value: 'projectCount', label: 'Nombre de projets' }
  ];

  constructor(
    private clientService: ClientService,
    private contactService: ContactService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {
    // Initialize filter form
    this.filterForm = this.fb.group({
      // Basic filters
      searchQuery: [''],
      searchType: ['CLIENT'],
      status: ['ALL'],

      // Advanced filters
      industry: [''],
      location: [''],
      projectCountRange: [''],
      createdDateFrom: [''],
      createdDateTo: [''],
      lastContactFrom: [''],
      lastContactTo: [''],
      selectedIndustries: [[]],

      // Sorting
      sortBy: ['name'],
      sortOrder: ['asc']
    });

    // Setup search debouncing
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.performSearch(query);
    });

    // Setup form value changes
    this.filterForm.valueChanges.pipe(
      debounceTime(300),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.applyAdvancedFilters();
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
        // TODO: Get freelanceId from authentication context
        const freelanceId = 1; // Temporary hardcoded value
        this.clientService.searchClients(freelanceId, query)
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
      case 'FINAL':
        return 'primary';
      case 'PROSPECT':
        return 'accent';
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
      case 'FINAL':
        return 'Client Final';
      case 'PROSPECT':
        return 'Prospect';
      case 'ACTIVE':
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      default:
        return status;
    }
  }

  // Advanced filtering methods
  applyAdvancedFilters(): void {
    const filters = this.filterForm.value;

    let filtered = this.clients.filter(client => {
      // Search query filter
      if (filters.searchQuery && filters.searchQuery.trim()) {
        const query = filters.searchQuery.toLowerCase();
        const searchableText = [
          client.companyName || client.name,
          client.notes,
          client.address,
          client.city,
          client.domain
        ].filter(Boolean).join(' ').toLowerCase();

        if (!searchableText.includes(query)) {
          return false;
        }
      }

      // Status filter based on isFinal
      if (filters.status && filters.status !== 'ALL') {
        const clientStatus = client.isFinal ? 'FINAL' : 'PROSPECT';
        if (clientStatus !== filters.status && client.status !== filters.status) {
          return false;
        }
      }

      // Industry filter (using domain as industry substitute)
      if (filters.industry && client.domain && !client.domain.toLowerCase().includes(filters.industry.toLowerCase())) {
        return false;
      }

      // Selected industries filter (using domain as industry substitute)
      if (filters.selectedIndustries && filters.selectedIndustries.length > 0) {
        const hasMatchingIndustry = filters.selectedIndustries.some((industry: string) =>
          client.domain && client.domain.toLowerCase().includes(industry.toLowerCase())
        );
        if (!hasMatchingIndustry) {
          return false;
        }
      }

      // Location filter
      if (filters.location && filters.location.trim()) {
        const locationQuery = filters.location.toLowerCase();
        const clientLocation = [client.address, client.city]
          .filter(Boolean).join(' ').toLowerCase();
        if (!clientLocation.includes(locationQuery)) {
          return false;
        }
      }

      // Project count range filter
      if (filters.projectCountRange && client.totalProjects !== undefined) {
        const projectCount = client.totalProjects;
        switch (filters.projectCountRange) {
          case '0':
            if (projectCount !== 0) { return false; }
            break;
          case '1-5':
            if (projectCount < 1 || projectCount > 5) { return false; }
            break;
          case '6-10':
            if (projectCount < 6 || projectCount > 10) { return false; }
            break;
          case '11-20':
            if (projectCount < 11 || projectCount > 20) { return false; }
            break;
          case '20+':
            if (projectCount < 20) { return false; }
            break;
        }
      }

      // Date filters
      if (filters.createdDateFrom && client.createdAt) {
        const createdDate = new Date(client.createdAt);
        const filterDate = new Date(filters.createdDateFrom);
        if (createdDate < filterDate) {
          return false;
        }
      }

      if (filters.createdDateTo && client.createdAt) {
        const createdDate = new Date(client.createdAt);
        const filterDate = new Date(filters.createdDateTo);
        if (createdDate > filterDate) {
          return false;
        }
      }

      // TODO: Implement lastContactDate filtering when backend supports it
      // if (filters.lastContactFrom && client.lastContactDate) {
      //   const lastContactDate = new Date(client.lastContactDate);
      //   const filterDate = new Date(filters.lastContactFrom);
      //   if (lastContactDate < filterDate) {
      //     return false;
      //   }
      // }

      // if (filters.lastContactTo && client.lastContactDate) {
      //   const lastContactDate = new Date(client.lastContactDate);
      //   const filterDate = new Date(filters.lastContactTo);
      //   if (lastContactDate > filterDate) {
      //     return false;
      //   }
      // }

      return true;
    });

    // Apply sorting
    if (filters.sortBy) {
      filtered = this.sortClients(filtered, filters.sortBy, filters.sortOrder);
    }

    this.filteredClients = filtered;
  }

  private sortClients(clients: ClientDto[], sortBy: string, sortOrder: string): ClientDto[] {
    return clients.sort((a, b) => {
      let aValue: string | number | Date;
      let bValue: string | number | Date;

      switch (sortBy) {
        case 'name':
          aValue = a.name?.toLowerCase() || '';
          bValue = b.name?.toLowerCase() || '';
          break;
        case 'industry':
          aValue = a.industry?.toLowerCase() || '';
          bValue = b.industry?.toLowerCase() || '';
          break;
        case 'createdAt':
          aValue = a.createdAt ? new Date(a.createdAt).getTime() : 0;
          bValue = b.createdAt ? new Date(b.createdAt).getTime() : 0;
          break;
        case 'lastContact':
          // TODO: Implement when backend supports lastContactDate
          aValue = 0;
          bValue = 0;
          break;
        case 'projectCount':
          aValue = a.totalProjects || 0;
          bValue = b.totalProjects || 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) {
        return sortOrder === 'asc' ? -1 : 1;
      }
      if (aValue > bValue) {
        return sortOrder === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }

  toggleAdvancedFilters(): void {
    this.showAdvancedFilters = !this.showAdvancedFilters;
  }

  addIndustry(industry: string): void {
    const selectedIndustries = this.filterForm.get('selectedIndustries')?.value || [];
    if (!selectedIndustries.includes(industry)) {
      selectedIndustries.push(industry);
      this.filterForm.get('selectedIndustries')?.setValue([...selectedIndustries]);
    }
  }

  removeIndustry(industry: string): void {
    const selectedIndustries = this.filterForm.get('selectedIndustries')?.value || [];
    const index = selectedIndustries.indexOf(industry);
    if (index >= 0) {
      selectedIndustries.splice(index, 1);
      this.filterForm.get('selectedIndustries')?.setValue([...selectedIndustries]);
    }
  }

  clearAdvancedFilters(): void {
    this.filterForm.reset({
      searchQuery: '',
      searchType: 'CLIENT',
      status: 'ALL',
      sortBy: 'name',
      sortOrder: 'asc'
    });
    this.applyAdvancedFilters();
  }

  getFilterCount(): number {
    const filters = this.filterForm.value;
    let count = 0;

    if (filters.searchQuery?.trim()) { count++; }
    if (filters.status && filters.status !== 'ALL') { count++; }
    if (filters.industry) { count++; }
    if (filters.selectedIndustries?.length > 0) { count++; }
    if (filters.location?.trim()) { count++; }
    if (filters.projectCountRange) { count++; }
    if (filters.createdDateFrom) { count++; }
    if (filters.createdDateTo) { count++; }
    if (filters.lastContactFrom) { count++; }
    if (filters.lastContactTo) { count++; }

    return count;
  }

  editClient(client: ClientDto): void {
    // Navigate to edit form or open edit dialog
    // TODO: Implement client editing functionality
  }
}

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
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

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
        MatTooltipModule,
        MatCheckboxModule,
        MatDatepickerModule,
        MatNativeDateModule,
        LoadingComponent,
        TranslateModule
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

  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  // Filter options
  industryOptions = [
    'Technologie', 'Finance', 'Santé', 'Éducation', 'Commerce', 'Industrie',
    'Services', 'Immobilier', 'Transport', 'Énergie', 'Média', 'Autre'
  ];

  statusOptions = [
    { value: 'ACTIVE', labelKey: 'common.active' },
    { value: 'INACTIVE', labelKey: 'common.inactive' },
    { value: 'ESN', labelKey: 'clients.esn' },
    { value: 'ARCHIVED', labelKey: 'clients.archived' }
  ];

  projectCountRanges = [
    { value: '0', labelKey: 'clients.noProjects' },
    { value: '1-5', labelKey: 'clients.projects1to5' },
    { value: '6-10', labelKey: 'clients.projects6to10' },
    { value: '11-20', labelKey: 'clients.projects11to20' },
    { value: '20+', labelKey: 'clients.projects20plus' }
  ];

  sortOptions = [
    { value: 'name', labelKey: 'clients.name' },
    { value: 'industry', labelKey: 'clients.industry' },
    { value: 'createdAt', labelKey: 'clients.createdDate' },
    { value: 'lastContact', labelKey: 'clients.lastContact' },
    { value: 'projectCount', labelKey: 'clients.projectCount' }
  ];

  constructor(
    private readonly clientService: ClientService,
    private readonly contactService: ContactService,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
    private readonly fb: FormBuilder,
    private readonly translateService: TranslateService
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
          this.snackBar.open(this.translateService.instant('errors.loadingClients'), this.translateService.instant('common.close'), {
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
        // Using default freelanceId until authentication context is implemented
        const freelanceId = 1;
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
            const clientIds = new Set(contacts.map(contact => contact.clientId));

            // Filter clients to only show those with matching contacts
            const matchingClients = this.clients.filter(client =>
              clientIds.has(client.id)
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
    if (confirm(this.translateService.instant('clients.confirmDelete', { name: client.name }))) {
      this.clientService.deleteClient(client.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open(this.translateService.instant('clients.deleteSuccess'), this.translateService.instant('common.close'), {
              duration: 3000
            });
            this.loadClients();
          },
          error: (error) => {
            console.error('Error deleting client:', error);
            this.snackBar.open(this.translateService.instant('errors.deletingClient'), this.translateService.instant('common.close'), {
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
      case 'ESN':
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
        return this.translateService.instant('clients.finalClient');
      case 'ESN':
        return this.translateService.instant('clients.esn');
      case 'ACTIVE':
        return this.translateService.instant('common.active');
      case 'INACTIVE':
        return this.translateService.instant('common.inactive');
      default:
        return status;
    }
  }

  // Advanced filtering methods
  applyAdvancedFilters(): void {
    const filters = this.filterForm.value;

    let filtered = this.clients.filter(client => this.matchesAllFilters(client, filters));

    // Apply sorting
    if (filters.sortBy) {
      filtered = this.sortClients(filtered, filters.sortBy, filters.sortOrder);
    }

    this.filteredClients = filtered;
  }

  private matchesAllFilters(client: ClientDto, filters: any): boolean {
    return this.matchesSearchQuery(client, filters)
      && this.matchesStatus(client, filters)
      && this.matchesIndustry(client, filters)
      && this.matchesSelectedIndustries(client, filters)
      && this.matchesLocation(client, filters)
      && this.matchesProjectCount(client, filters)
      && this.matchesDateRange(client, filters);
  }

  private matchesSearchQuery(client: ClientDto, filters: any): boolean {
    if (!filters.searchQuery?.trim()) { return true; }
    const query = filters.searchQuery.toLowerCase();
    const searchableText = [
      client.companyName || client.name,
      client.notes,
      client.address,
      client.city,
      client.domain
    ].filter(Boolean).join(' ').toLowerCase();
    return searchableText.includes(query);
  }

  private matchesStatus(client: ClientDto, filters: any): boolean {
    if (!filters.status || filters.status === 'ALL') { return true; }
    const clientStatus = client.isFinal ? 'FINAL' : 'ESN';
    return clientStatus === filters.status || client.status === filters.status;
  }

  private matchesIndustry(client: ClientDto, filters: any): boolean {
    if (!filters.industry || !client.domain) { return true; }
    return client.domain.toLowerCase().includes(filters.industry.toLowerCase());
  }

  private matchesSelectedIndustries(client: ClientDto, filters: any): boolean {
    if (!filters.selectedIndustries?.length) { return true; }
    return filters.selectedIndustries.some((industry: string) =>
      client.domain?.toLowerCase().includes(industry.toLowerCase())
    );
  }

  private matchesLocation(client: ClientDto, filters: any): boolean {
    if (!filters.location?.trim()) { return true; }
    const locationQuery = filters.location.toLowerCase();
    const clientLocation = [client.address, client.city]
      .filter(Boolean).join(' ').toLowerCase();
    return clientLocation.includes(locationQuery);
  }

  private matchesProjectCount(client: ClientDto, filters: any): boolean {
    if (!filters.projectCountRange || client.totalProjects === undefined) { return true; }
    const count = client.totalProjects;
    switch (filters.projectCountRange) {
      case '0': return count === 0;
      case '1-5': return count >= 1 && count <= 5;
      case '6-10': return count >= 6 && count <= 10;
      case '11-20': return count >= 11 && count <= 20;
      case '20+': return count >= 20;
      default: return true;
    }
  }

  private matchesDateRange(client: ClientDto, filters: any): boolean {
    if (filters.createdDateFrom && client.createdAt) {
      if (new Date(client.createdAt) < new Date(filters.createdDateFrom)) { return false; }
    }
    if (filters.createdDateTo && client.createdAt) {
      if (new Date(client.createdAt) > new Date(filters.createdDateTo)) { return false; }
    }
    return true;
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
          // Last contact date comparison pending indezy-server support
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
    // Client editing functionality pending implementation
    console.warn('Client editing not yet implemented', client);
  }
}

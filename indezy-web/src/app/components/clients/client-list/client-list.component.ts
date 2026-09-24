import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Subject, takeUntil, debounceTime } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { ClientService } from '../../../services/client/client.service';
import { ClientDto } from '../../../models';
import { LoadingComponent } from '../../../shared/components/loading/loading.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

export type ClientTypeFilter = 'ALL' | 'FINAL' | 'ESN' | 'BLACKLISTED';
export type ClientSortField = 'name' | 'city' | 'rating' | 'projects';

interface ClientFilterValues {
  searchQuery: string;
  type: ClientTypeFilter;
  city: string;
  sortBy: ClientSortField;
  sortOrder: 'asc' | 'desc';
}

const DEFAULT_FILTERS: ClientFilterValues = {
  searchQuery: '',
  type: 'ALL',
  city: '',
  sortBy: 'name',
  sortOrder: 'asc'
};

@Component({
    selector: 'app-client-list',
    imports: [
    RouterModule,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatPaginatorModule,
    LoadingComponent,
    TranslateModule
],
    templateUrl: './client-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./client-list.component.scss']
})
export class ClientListComponent implements OnInit, OnDestroy {
  clients: ClientDto[] = [];
  filteredClients: ClientDto[] = [];
  pagedClients: ClientDto[] = [];
  cities: string[] = [];
  displayedColumns: string[] = ['name', 'type', 'city', 'projects', 'contacts', 'actions'];
  isLoading = false;

  pageIndex = 0;
  pageSize = 25;
  readonly pageSizeOptions = [25, 50, 100];

  readonly typeOptions: { value: ClientTypeFilter; labelKey: string }[] = [
    { value: 'ALL', labelKey: 'clients.allTypes' },
    { value: 'FINAL', labelKey: 'clients.finalClients' },
    { value: 'ESN', labelKey: 'clients.esnPlural' },
    { value: 'BLACKLISTED', labelKey: 'clients.blacklistedPlural' }
  ];

  readonly sortOptions: { value: ClientSortField; labelKey: string }[] = [
    { value: 'name', labelKey: 'clients.name' },
    { value: 'city', labelKey: 'clients.city' },
    { value: 'rating', labelKey: 'clients.form.rating' },
    { value: 'projects', labelKey: 'clients.projectCount' }
  ];

  filterForm: FormGroup;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly clientService: ClientService,
    private readonly notificationService: NotificationService,
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly confirmDialog: ConfirmDialogService
  ) {
    this.filterForm = this.fb.group({ ...DEFAULT_FILTERS });
    this.filterForm.valueChanges.pipe(
      debounceTime(150),
      takeUntil(this.destroy$)
    ).subscribe(() => this.applyFilters());
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
    this.clientService.getForCurrentFreelance()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = clients;
          this.cities = [...new Set(clients.map(client => client.city).filter(Boolean))]
            .sort((a, b) => a.localeCompare(b));
          this.isLoading = false;
          this.applyFilters();
        },
        error: (error) => {
          console.error('Error loading clients:', error);
          this.notificationService.error('errors.loadingClients');
          this.isLoading = false;
        }
      });
  }

  applyFilters(): void {
    const filters: ClientFilterValues = { ...DEFAULT_FILTERS, ...this.filterForm.value };
    const query = filters.searchQuery.trim().toLowerCase();
    const filtered = this.clients.filter(client =>
      this.matchesType(client, filters.type) &&
      (!filters.city || client.city === filters.city) &&
      (!query || [client.companyName, client.city, client.address, client.notes, client.domain]
        .some(value => value?.toLowerCase().includes(query)))
    );
    this.filteredClients = this.sortClients(filtered, filters.sortBy, filters.sortOrder);
    this.pageIndex = 0;
    this.updatePage();
  }

  hasActiveFilters(): boolean {
    const filters: ClientFilterValues = { ...DEFAULT_FILTERS, ...this.filterForm.value };
    return !!filters.searchQuery.trim() || filters.type !== 'ALL' || !!filters.city;
  }

  clearFilters(): void {
    this.filterForm.reset({ ...DEFAULT_FILTERS });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePage();
  }

  openClient(client: ClientDto): void {
    this.router.navigate(['/clients', client.id]);
  }

  deleteClient(client: ClientDto): void {
    this.confirmDialog.confirm({
      messageKey: 'clients.confirmDelete',
      messageParams: { name: client.companyName },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.clientService.deleteClient(client.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.success('clients.deleteSuccess');
            this.loadClients();
          },
          error: (error) => {
            console.error('Error deleting client:', error);
            this.notificationService.error(
              error?.status === 409 ? 'clients.deleteInUse' : 'errors.deletingClient', 5000);
          }
        });
    });
  }

  /** External link for the client's website, tolerating values typed without a scheme. */
  websiteUrl(client: ClientDto): string | null {
    const value = client.domain?.trim();
    if (!value || !value.includes('.')) {
      return null;
    }
    return /^https?:\/\//i.test(value) ? value : `https://${value}`;
  }

  private matchesType(client: ClientDto, type: ClientTypeFilter): boolean {
    switch (type) {
      case 'FINAL':
        return client.isFinal !== false;
      case 'ESN':
        return client.isFinal === false;
      case 'BLACKLISTED':
        return !!client.isBlacklisted;
      default:
        return true;
    }
  }

  private sortClients(clients: ClientDto[], sortBy: ClientSortField, sortOrder: 'asc' | 'desc'): ClientDto[] {
    const direction = sortOrder === 'desc' ? -1 : 1;
    const value = (client: ClientDto): string | number => {
      switch (sortBy) {
        case 'city':
          return client.city?.toLowerCase() ?? '';
        case 'rating':
          return client.rating ?? 0;
        case 'projects':
          return client.totalProjects ?? 0;
        default:
          return client.companyName?.toLowerCase() ?? '';
      }
    };
    return [...clients].sort((a, b) => {
      const aValue = value(a);
      const bValue = value(b);
      const comparison = typeof aValue === 'number' && typeof bValue === 'number'
        ? aValue - bValue
        : String(aValue).localeCompare(String(bValue));
      return comparison * direction;
    });
  }

  private updatePage(): void {
    const lastPage = Math.max(0, Math.ceil(this.filteredClients.length / this.pageSize) - 1);
    this.pageIndex = Math.min(this.pageIndex, lastPage);
    const start = this.pageIndex * this.pageSize;
    this.pagedClients = this.filteredClients.slice(start, start + this.pageSize);
  }
}

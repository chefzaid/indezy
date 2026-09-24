import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { Subject, forkJoin, takeUntil } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { ProjectService } from '../../../services/project/project.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientDto, ContactDto, PROJECT_STATUS_COLORS, ProjectDto, ProjectStatus } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactImportDialogComponent } from '../../contacts/contact-import-dialog/contact-import-dialog.component';

/** Tab order of the client page; the "tab" query parameter selects one by name. */
const TABS = ['details', 'projects', 'contacts'] as const;

@Component({
    selector: 'app-client-detail',
    imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
    MatMenuModule,
    MatTooltipModule,
    MatDialogModule,
    TranslateModule
],
    templateUrl: './client-detail.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./client-detail.component.scss']
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  client?: ClientDto;
  contacts: ContactDto[] = [];
  /** Missions delivered for this client or intermediated by it. */
  projects: ProjectDto[] = [];
  isLoading = false;
  isLoadingContacts = false;
  clientId?: number;
  selectedTabIndex = 0;

  readonly statusColors = PROJECT_STATUS_COLORS;
  contactDisplayedColumns: string[] = ['name', 'email', 'phone', 'actions'];
  projectDisplayedColumns: string[] = ['role', 'status', 'dailyRate', 'startDate'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly clientService: ClientService,
    private readonly contactService: ContactService,
    private readonly projectService: ProjectService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly dialog: MatDialog,
    private readonly confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    const tab = this.route.snapshot.queryParamMap.get('tab');
    const tabIndex = TABS.indexOf(tab as typeof TABS[number]);
    this.selectedTabIndex = tabIndex >= 0 ? tabIndex : 0;

    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.clientId = +params['id'];
        this.loadClient();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadClient(): void {
    if (!this.clientId) {
      return;
    }
    const clientId = this.clientId;
    const freelanceId = this.authService.getUser()?.id;

    this.isLoading = true;
    forkJoin({
      client: this.clientService.getClient(clientId),
      contacts: this.contactService.getContactsByClient(clientId),
      projects: freelanceId ? this.projectService.getByFreelanceId(freelanceId) : this.projectService.getByClientId(clientId)
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ client, contacts, projects }) => {
          this.client = client;
          this.contacts = contacts;
          this.projects = projects
            .filter(project => project.clientId === clientId || project.middlemanId === clientId)
            .sort((a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''));
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading client:', error);
          this.notificationService.error(error?.status === 404 ? 'errors.clientNotFound' : 'errors.loadingClient');
          this.isLoading = false;
          if (error?.status === 404) {
            this.router.navigate(['/clients']);
          }
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
          this.notificationService.error('errors.loadingContacts');
          this.isLoadingContacts = false;
        }
      });
  }

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: index > 0 ? TABS[index] : null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  onEdit(): void {
    if (this.client) {
      this.router.navigate(['/clients', this.client.id, 'edit']);
    }
  }

  onDelete(): void {
    if (!this.client) {
      return;
    }
    const client = this.client;
    if (this.projects.length > 0) {
      this.notificationService.error('clients.deleteInUse', 5000);
      return;
    }
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
            this.router.navigate(['/clients']);
          },
          error: (error) => {
            console.error('Error deleting client:', error);
            this.notificationService.error(error?.status === 409 ? 'clients.deleteInUse' : 'errors.deletingClient', 5000);
          }
        });
    });
  }

  onBack(): void {
    this.router.navigate(['/clients']);
  }

  onNewProject(): void {
    this.router.navigate(['/projects/new']);
  }

  openProject(project: ProjectDto): void {
    this.router.navigate(['/projects', project.id]);
  }

  /** Role this client plays in a mission: the final client or the intermediary. */
  isIntermediaryFor(project: ProjectDto): boolean {
    return project.middlemanId === this.clientId && project.clientId !== this.clientId;
  }

  getProjectStatusColor(project: ProjectDto): string {
    return this.statusColors[project.status ?? ProjectStatus.IDENTIFIED];
  }

  /** External link for the client's website, tolerating values typed without a scheme. */
  websiteUrl(): string | null {
    const value = this.client?.domain?.trim();
    if (!value || !value.includes('.')) {
      return null;
    }
    return /^https?:\/\//i.test(value) ? value : `https://${value}`;
  }

  // Contact management methods
  onAddContact(): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'contacts', 'create']);
    }
  }

  onImportContacts(): void {
    if (!this.clientId) {
      return;
    }
    const clientId = this.clientId;
    this.dialog.open(ContactImportDialogComponent, { width: '560px', autoFocus: false })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((content: string | undefined) => {
        if (!content) {
          return;
        }
        this.contactService.importForClient(clientId, content)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: result => {
              this.notificationService.success('contacts.import.result', 4000,
                { imported: result.imported, skipped: result.skipped });
              this.loadContacts();
            },
            error: () => this.notificationService.error('contacts.import.error')
          });
      });
  }

  onEditContact(contact: ContactDto): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'contacts', contact.id, 'edit']);
    }
  }

  onViewContact(contact: ContactDto): void {
    this.router.navigate(['/contacts', contact.id]);
  }

  onDeleteContact(contact: ContactDto): void {
    if (!contact.id) {
      this.notificationService.error('errors.missingContactId');
      return;
    }

    const contactId = contact.id;
    this.confirmDialog.confirm({
      messageKey: 'contacts.confirmDelete',
      messageParams: { name: this.getContactName(contact) },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.contactService.deleteContact(contactId)
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

  getContactName(contact: ContactDto): string {
    return [contact.firstName, contact.lastName].filter(Boolean).join(' ');
  }
}

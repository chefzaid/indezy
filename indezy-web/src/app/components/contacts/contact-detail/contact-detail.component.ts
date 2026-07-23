import { Component, OnInit, OnDestroy } from '@angular/core';

import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactDto } from '../../../models';

@Component({
    selector: 'app-contact-detail',
    imports: [
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule
],
    templateUrl: './contact-detail.component.html',
    styleUrls: ['./contact-detail.component.scss']
})
export class ContactDetailComponent implements OnInit, OnDestroy {
  contact?: ContactDto;
  isLoading = false;
  contactId?: number;
  
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly contactService: ContactService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.contactId = +params['id'];
        this.loadContact();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadContact(): void {
    if (!this.contactId) { return; }
    
    this.isLoading = true;
    this.contactService.getContact(this.contactId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (contact) => {
          if (contact) {
            this.contact = contact;
          } else {
            this.notificationService.error('errors.contactNotFound');
            this.router.navigate(['/contacts']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading contact:', error);
          this.notificationService.error('errors.loadingContact');
          this.isLoading = false;
        }
      });
  }

  onEdit(): void {
    if (this.contact) {
      this.router.navigate(['/contacts', this.contact.id, 'edit']);
    }
  }

  onDelete(): void {
    if (!this.contact || !this.contact.id) {
      this.notificationService.error('errors.missingContactId');
      return;
    }

    const contactId = this.contact.id;
    this.confirmDialog.confirm({
      messageKey: 'contacts.deleteConfirm',
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
            this.router.navigate(['/contacts']);
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.notificationService.error('errors.deletingContact');
          }
        });
    });
  }

  onBack(): void {
    this.router.navigate(['/contacts']);
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
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      default:
        return status;
    }
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('fr-FR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  sendEmail(): void {
    if (this.contact?.email) {
      window.location.href = `mailto:${this.contact.email}`;
    }
  }

  callPhone(): void {
    if (this.contact?.phone) {
      window.location.href = `tel:${this.contact.phone}`;
    }
  }

  getFullName(): string {
    if (!this.contact) { return ''; }
    return `${this.contact.firstName} ${this.contact.lastName}`;
  }
}

import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';

import { DatePipe } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactDto, contactName } from '../../../models';
import { ContactInteractionsComponent } from '../contact-interactions/contact-interactions.component';
import { ContactNetworkComponent } from '../contact-network/contact-network.component';
import { saveBlob } from '../../../shared/utils/download-file';

@Component({
    selector: 'app-contact-detail',
    imports: [
    DatePipe,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    TranslateModule,
    ContactInteractionsComponent,
    ContactNetworkComponent
],
    templateUrl: './contact-detail.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
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
    this.contactService.getById(this.contactId)
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
          this.notificationService.error(error?.status === 404 ? 'errors.contactNotFound' : 'errors.loadingContact');
          this.isLoading = false;
          // Nothing to show or edit: go back to the list instead of an empty page.
          this.router.navigate(['/contacts']);
        }
      });
  }

  onEdit(): void {
    if (this.contact) {
      this.router.navigate(['/contacts', this.contact.id, 'edit']);
    }
  }

  onDelete(): void {
    if (!this.contact?.id) {
      this.notificationService.error('errors.missingContactId');
      return;
    }

    const contactId = this.contact.id;
    this.confirmDialog.confirm({
      messageKey: 'contacts.confirmDelete',
      messageParams: { name: this.getFullName() },
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
            this.router.navigate(['/contacts']);
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.notificationService.error('errors.deletingContact');
          }
        });
    });
  }

  /** Downloads what the workspace holds about the contact, to hand them on request. */
  onPersonalData(): void {
    const contactId = this.contact!.id!;
    this.contactService.personalData(contactId).pipe(takeUntil(this.destroy$)).subscribe({
      next: blob => saveBlob(blob, `contact-${contactId}.json`),
      error: () => this.notificationService.error('contacts.privacy.dataError')
    });
  }

  /** Erases the contact on their request, once confirmed; the request is kept in the account's activity. */
  onErase(): void {
    const contactId = this.contact!.id!;
    this.confirmDialog.confirm({
      titleKey: 'contacts.privacy.eraseTitle',
      messageKey: 'contacts.privacy.eraseMessage',
      messageParams: { name: this.getFullName() },
      confirmKey: 'contacts.privacy.erase',
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.contactService.erase(contactId).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.notificationService.success('contacts.privacy.erased');
          this.router.navigate(['/contacts']);
        },
        error: () => this.notificationService.error('contacts.privacy.eraseError')
      });
    });
  }

  onBack(): void {
    this.router.navigate(['/contacts']);
  }

  getFullName(): string {
    if (!this.contact) { return ''; }
    if (this.contact.anonymizedAt) {
      return this.translate.instant('contacts.privacy.anonymized');
    }
    return contactName(this.contact);
  }
}

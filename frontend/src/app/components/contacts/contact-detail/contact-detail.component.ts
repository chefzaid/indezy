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
import { Subject, takeUntil } from 'rxjs';

import { ContactService } from '../../../services/contact/contact.service';
import { ContactDto } from '../../../models';

@Component({
    selector: 'app-contact-detail',
    imports: [
        CommonModule,
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
    private readonly snackBar: MatSnackBar
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
            this.snackBar.open('Contact non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/contacts']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading contact:', error);
          this.snackBar.open('Erreur lors du chargement du contact', 'Fermer', { duration: 3000 });
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
    if (!this.contact) { return; }
    
    if (confirm(`Êtes-vous sûr de vouloir supprimer le contact "${this.contact.firstName} ${this.contact.lastName}" ?`)) {
      this.contactService.deleteContact(this.contact.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Contact supprimé avec succès', 'Fermer', { duration: 3000 });
            this.router.navigate(['/contacts']);
          },
          error: (error) => {
            console.error('Error deleting contact:', error);
            this.snackBar.open('Erreur lors de la suppression du contact', 'Fermer', { duration: 3000 });
          }
        });
    }
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

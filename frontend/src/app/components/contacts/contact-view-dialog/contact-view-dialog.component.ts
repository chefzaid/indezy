import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { ContactDto } from '../../../services/contact.service';

@Component({
    selector: 'app-contact-view-dialog',
    imports: [
        CommonModule,
        MatDialogModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        MatChipsModule,
        MatDividerModule,
        MatTooltipModule,
        MatSnackBarModule
    ],
    templateUrl: './contact-view-dialog.component.html',
    styleUrls: ['./contact-view-dialog.component.scss']
})
export class ContactViewDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ContactViewDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public contact: ContactDto,
    private snackBar: MatSnackBar
  ) {}

  onClose(): void {
    this.dialogRef.close();
  }

  onEdit(): void {
    this.dialogRef.close('edit');
  }

  sendEmail(): void {
    if (this.contact.email) {
      window.open(`mailto:${this.contact.email}`, '_blank');
    }
  }

  callPhone(): void {
    if (this.contact.phone) {
      window.open(`tel:${this.contact.phone}`, '_blank');
    }
  }

  getFullName(): string {
    return `${this.contact.firstName} ${this.contact.lastName || ''}`.trim();
  }

  getStatusColor(status: string): 'primary' | 'accent' | 'warn' {
    switch (status) {
      case 'ACTIVE':
        return 'primary';
      case 'INACTIVE':
        return 'warn';
      default:
        return 'accent';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      default:
        return 'Inconnu';
    }
  }

  async copyToClipboard(text: string, type: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.snackBar.open(`${type} copié dans le presse-papiers`, 'Fermer', {
        duration: 2000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom'
      });
    } catch (err) {
      console.error('Erreur lors de la copie:', err);
      this.snackBar.open(`Erreur lors de la copie du ${type.toLowerCase()}`, 'Fermer', {
        duration: 3000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom'
      });
    }
  }
}

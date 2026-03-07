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
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ContactDto } from '../../../services/contact/contact.service';

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
        MatSnackBarModule,
        TranslateModule
    ],
    templateUrl: './contact-view-dialog.component.html',
    styleUrls: ['./contact-view-dialog.component.scss']
})
export class ContactViewDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ContactViewDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public contact: ContactDto,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
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
        return this.translate.instant('common.active');
      case 'INACTIVE':
        return this.translate.instant('common.inactive');
      default:
        return this.translate.instant('common.unknown');
    }
  }

  async copyToClipboard(text: string, typeKey: string): Promise<void> {
    const type = this.translate.instant(typeKey);
    try {
      await navigator.clipboard.writeText(text);
      this.snackBar.open(this.translate.instant('common.copiedToClipboard', { type }), this.translate.instant('common.close'), {
        duration: 2000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom'
      });
    } catch (err) {
      console.error('Erreur lors de la copie:', err);
      this.snackBar.open(this.translate.instant('errors.copying', { type: type.toLowerCase() }), this.translate.instant('common.close'), {
        duration: 3000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom'
      });
    }
  }
}

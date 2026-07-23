import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ConfirmDialogComponent, ConfirmDialogData } from '../components/confirm-dialog/confirm-dialog.component';

/**
 * Opens the reusable {@link ConfirmDialogComponent} and exposes the user's choice
 * as a boolean stream, providing a consistent replacement for native `confirm()`.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  constructor(private readonly dialog: MatDialog) {}

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open(ConfirmDialogComponent, {
        data,
        width: '420px',
        autoFocus: false,
        restoreFocus: true
      })
      .afterClosed()
      .pipe(map(result => result === true));
  }
}

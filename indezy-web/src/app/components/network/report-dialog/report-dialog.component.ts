import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { REPORT_REASONS, ReportReason, ReportTarget } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** What is reported: a post or a comment by id, a member by handle. */
export interface ReportSubject {
  target: ReportTarget;
  id: number | string;
}

/** Reports a post, a comment or a member to the moderators, with a reason and, if wanted, a few words. */
@Component({
  selector: 'app-report-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './report-dialog.component.html',
  styles: ['.report-form { display: flex; flex-direction: column; min-width: min(420px, 80vw); padding-top: 4px; } .hint { margin-top: 0; }'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ReportDialogComponent {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<ReportDialogComponent, boolean>>(MatDialogRef);
  readonly subject = inject<ReportSubject>(MAT_DIALOG_DATA);
  readonly reasons = REPORT_REASONS;
  readonly form = inject(FormBuilder).nonNullable.group({
    reason: ['' as ReportReason | '', Validators.required],
    details: ['', Validators.maxLength(500)]
  });
  sending = false;

  /** Opens the dialog; it emits whether the report was sent. */
  static open(dialog: MatDialog, subject: ReportSubject): Observable<boolean | undefined> {
    return dialog.open<ReportDialogComponent, ReportSubject, boolean>(ReportDialogComponent, { data: subject }).afterClosed();
  }

  send(): void {
    if (this.form.invalid || this.sending) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending = true;
    const { reason, details } = this.form.getRawValue();
    this.network.report(this.subject.target, this.subject.id, reason as ReportReason, details.trim() || null).subscribe({
      next: () => {
        this.notifications.success('network.report.sent');
        this.dialogRef.close(true);
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        const message: string = error.error?.message ?? '';
        if (message.startsWith('You already reported')) {
          this.notifications.error('network.report.already');
          this.dialogRef.close(false);
        } else if (message.startsWith('The moderators suspended')) {
          this.notifications.error('network.report.suspended');
        } else {
          this.notifications.error('network.report.error');
        }
      }
    });
  }
}

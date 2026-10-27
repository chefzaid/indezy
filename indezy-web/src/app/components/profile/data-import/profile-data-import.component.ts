import { ChangeDetectionStrategy, Component, OnDestroy, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { WorkspaceImportReport, WorkspaceImportSection } from '../../../models/user-management.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';

/** Largest file the server takes, as its upload limit. */
export const IMPORT_FILE_MAX_BYTES = 10 * 1024 * 1024;

/**
 * Imports an Indezy account export into the workspace, preview first: each section says what is new, already there or
 * left out (and why) before anything is written; importing then reports what was added.
 */
@Component({
  selector: 'app-profile-data-import',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './profile-data-import.component.html',
  styleUrls: ['./profile-data-import.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileDataImportComponent implements OnDestroy {
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();

  file?: File;
  report?: WorkspaceImportReport;
  isBusy = false;

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Previews the chosen file; a file over the upload limit is refused before it is sent. */
  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > IMPORT_FILE_MAX_BYTES) {
      this.notifications.error('profile.import.tooLarge');
      return;
    }
    this.file = file;
    this.report = undefined;
    this.isBusy = true;
    this.users.previewImport(file).pipe(takeUntil(this.destroy$)).subscribe({
      next: report => {
        this.report = report;
        this.isBusy = false;
      },
      error: (error: HttpErrorResponse) => this.fail(error)
    });
  }

  /** Writes the previewed file's new records. */
  confirm(): void {
    if (!this.file || this.isBusy || !this.canImport) {
      return;
    }
    this.isBusy = true;
    this.users.importData(this.file).pipe(takeUntil(this.destroy$)).subscribe({
      next: report => {
        this.report = report;
        this.file = undefined;
        this.isBusy = false;
        this.notifications.success('profile.import.done');
      },
      error: (error: HttpErrorResponse) => this.fail(error)
    });
  }

  cancel(): void {
    this.file = undefined;
    this.report = undefined;
  }

  /** Whether the previewed file holds anything new. */
  get canImport(): boolean {
    return !!this.report && !this.report.applied && this.report.sections.some(section => section.added > 0);
  }

  /** The records worth listing: the ones left out first, then the new ones; duplicates are only counted. */
  listed(section: WorkspaceImportSection): WorkspaceImportSection['items'] {
    return [...section.items.filter(item => item.status === 'INVALID'), ...section.items.filter(item => item.status === 'NEW')];
  }

  private fail(error: HttpErrorResponse): void {
    this.isBusy = false;
    this.file = undefined;
    this.report = undefined;
    this.notifications.error(error.status === 400 ? 'profile.import.invalidFile' : 'profile.import.error');
  }
}

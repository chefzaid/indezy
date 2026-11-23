import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { ProfileCv } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileCvService } from '../../../services/profile-cv/profile-cv.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../../shared/utils/download-file';

/** The CV kept in the profile (see docs/features.md#profile-import): download it, replace it or remove it. */
@Component({
  selector: 'app-profile-cv-card',
  imports: [DatePipe, MatButtonModule, MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cv" aria-labelledby="profile-cv-title" data-profile-cv>
      <h2 id="profile-cv-title">{{ 'profileCv.title' | translate }}</h2>
      <input #file type="file" accept=".pdf,.docx,.doc,.odt" hidden (change)="keep($event)" data-profile-cv-file
        [attr.aria-label]="'profileCv.keep' | translate">
      @if (cv) {
        <p class="kept" data-profile-cv-kept>
          <mat-icon aria-hidden="true">description</mat-icon>
          <span>{{ cv.fileName }} · {{ 'profileCv.keptOn' | translate:{ date: (cv.keptAt | date:'mediumDate') } }}</span>
        </p>
        <div class="actions">
          <button mat-stroked-button type="button" (click)="download()" data-profile-cv-download>
            <mat-icon>download</mat-icon>{{ 'profileCv.download' | translate }}
          </button>
          <button mat-button type="button" (click)="file.click()" [disabled]="busy" data-profile-cv-replace>
            <mat-icon>upload_file</mat-icon>{{ 'profileCv.replace' | translate }}
          </button>
          <button mat-button type="button" (click)="remove()" data-profile-cv-remove>
            <mat-icon>delete</mat-icon>{{ 'common.delete' | translate }}
          </button>
        </div>
      } @else {
        <p class="hint">{{ 'profileCv.none' | translate }}</p>
        <button mat-stroked-button type="button" (click)="file.click()" [disabled]="busy" data-profile-cv-add>
          <mat-icon>upload_file</mat-icon>{{ 'profileCv.keep' | translate }}
        </button>
      }
    </section>
  `,
  styles: [`
    .cv { margin-top: 24px; }
    .cv h2 { margin: 0 0 8px; font-size: 1.15rem; }
    .hint { color: var(--app-text-muted); font-size: 0.9rem; margin: 0 0 8px; }
    .kept { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; overflow-wrap: anywhere; }
    .kept mat-icon { flex: none; color: var(--app-link); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
  `]
})
export class ProfileCvCardComponent implements OnInit {
  private readonly cvs = inject(ProfileCvService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  cv: ProfileCv | null = null;
  busy = false;

  ngOnInit(): void {
    this.reload();
    this.cvs.changed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reload());
  }

  reload(): void {
    this.cvs.info().subscribe({
      next: cv => this.show(cv),
      error: () => this.show(null)
    });
  }

  keep(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.busy = true;
    this.cvs.keep(file).subscribe({
      next: cv => {
        this.busy = false;
        this.notifications.success('profileCv.kept');
        this.show(cv);
      },
      error: (error: HttpErrorResponse) => {
        this.busy = false;
        this.notifications.error(error.status === 400 ? 'profileCv.refused' : 'profileCv.keepError');
        this.changes.markForCheck();
      }
    });
  }

  download(): void {
    const name = this.cv?.fileName ?? 'cv.pdf';
    this.cvs.download().subscribe({
      next: blob => saveBlob(blob, name),
      error: () => this.notifications.error('profileCv.downloadError')
    });
  }

  remove(): void {
    this.confirm.confirm({ titleKey: 'profileCv.removeTitle', messageKey: 'profileCv.removeMessage', confirmKey: 'common.delete', danger: true })
      .pipe(filter(Boolean), switchMap(() => this.cvs.remove())).subscribe({
        next: () => this.show(null),
        error: () => this.notifications.error('profileCv.removeError')
      });
  }

  private show(cv: ProfileCv | null): void {
    this.cv = cv;
    this.changes.markForCheck();
  }
}

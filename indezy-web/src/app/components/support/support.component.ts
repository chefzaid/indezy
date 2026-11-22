import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, concatMap, from, map, of, toArray } from 'rxjs';
import { SUPPORT_FILES_MAX, SUPPORT_FILE_MAX_BYTES, SUPPORT_KINDS, SupportAttachment, SupportKind, SupportRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { SupportService } from '../../services/support/support.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { saveBlob } from '../../shared/utils/download-file';
import { version as APP_VERSION } from '../../../../package.json';

/**
 * Support (`/support`, see docs/features.md#support-requests): a request to the Indezy team, with the technical context
 * shown before sending and sent unless unticked, up to three screenshots or PDFs, and the account's requests with their
 * answers and files. `?kind=` and `?error=` prefill a request, as the error toast's **Report** does.
 */
@Component({
  selector: 'app-support',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule, PageHeaderComponent],
  templateUrl: './support.component.html',
  styleUrls: ['./support.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SupportComponent implements OnInit {
  private readonly support = inject(SupportService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly changes = inject(ChangeDetectorRef);

  readonly kinds = SUPPORT_KINDS;
  readonly form = inject(FormBuilder).nonNullable.group({
    kind: ['PROBLEM' as SupportKind, Validators.required],
    subject: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]],
    sendContext: [true]
  });
  requests: SupportRequest[] = [];
  /** The screenshots or PDFs chosen to go with the request. */
  files: File[] = [];
  sending = false;
  private error: string | null = null;

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const kind = params.get('kind') as SupportKind | null;
    if (kind && SUPPORT_KINDS.includes(kind)) {
      this.form.patchValue({ kind });
    }
    this.error = params.get('error')?.slice(0, 300) ?? null;
    this.load();
  }

  /** What is sent besides the request, shown before sending: the page, the browser, the screen, the language and the error. */
  get context(): string {
    const page = this.route.snapshot.queryParamMap.get('page') ?? document.referrer.replace(location.origin, '') ?? '';
    return [`${this.translate.instant('support.context.page')}: ${page || '/support'}`,
      `${this.translate.instant('support.context.version')}: ${APP_VERSION}`,
      `${this.translate.instant('support.context.browser')}: ${navigator.userAgent}`,
      `${this.translate.instant('support.context.screen')}: ${window.innerWidth}×${window.innerHeight}`,
      `${this.translate.instant('support.context.language')}: ${this.translate.currentLang ?? ''}`,
      ...(this.error ? [`${this.translate.instant('support.context.error')}: ${this.error}`] : [])].join('\n');
  }

  send(): void {
    if (this.form.invalid || this.sending) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const files = this.files;
    this.sending = true;
    this.support.send({ kind: value.kind, subject: value.subject.trim(), description: value.description.trim(),
      context: value.sendContext ? this.context.slice(0, 2000) : null }).pipe(
      // The request goes first, then its files one by one; a file refused leaves the request sent.
      concatMap(request => from(files).pipe(
        concatMap(file => this.support.attach(request.id, file).pipe(catchError(() => {
          this.notifications.error('support.fileError', 6000, { name: file.name });
          return of(null);
        }))),
        toArray(),
        map(added => ({ ...request, attachments: added.filter((attachment): attachment is SupportAttachment => attachment !== null) })))))
      .subscribe({
      next: request => {
        this.sending = false;
        this.requests = [request, ...this.requests];
        this.files = [];
        this.form.reset({ kind: value.kind, subject: '', description: '', sendContext: true });
        this.notifications.success('support.sent');
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        this.notifications.error(error.status === 409 ? 'support.dailyLimit' : 'support.sendError');
        this.changes.markForCheck();
      }
    });
  }

  /** Adds the chosen files, up to three of 5 MB each, screenshots or PDFs; the others are said and left out. */
  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    for (const file of Array.from(input.files ?? [])) {
      if (this.files.length >= SUPPORT_FILES_MAX) {
        this.notifications.error('support.filesMax', 6000, { max: SUPPORT_FILES_MAX });
        break;
      }
      if (file.size > SUPPORT_FILE_MAX_BYTES || !/^(image\/(png|jpeg|gif|webp)|application\/pdf)$/.test(file.type)) {
        this.notifications.error('support.fileRefused', 6000, { name: file.name });
      } else {
        this.files = [...this.files, file];
      }
    }
    input.value = '';
  }

  remove(file: File): void {
    this.files = this.files.filter(chosen => chosen !== file);
  }

  download(attachment: SupportAttachment): void {
    this.support.file(attachment).subscribe({
      next: blob => saveBlob(blob, attachment.fileName),
      error: () => this.notifications.error('support.downloadError')
    });
  }

  private load(): void {
    this.support.mine().subscribe({
      next: requests => {
        this.requests = requests;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('support.loadError')
    });
  }
}

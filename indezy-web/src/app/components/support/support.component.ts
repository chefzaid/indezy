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
import { SUPPORT_KINDS, SupportKind, SupportRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { SupportService } from '../../services/support/support.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/**
 * Support (`/support`, see docs/features.md#support-requests): a request to the Indezy team, with the technical context
 * shown before sending and sent unless unticked, and the account's requests with their answers. `?kind=` and `?error=`
 * prefill a request, as the error toast's **Report** does.
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
    this.sending = true;
    this.support.send({ kind: value.kind, subject: value.subject.trim(), description: value.description.trim(),
      context: value.sendContext ? this.context.slice(0, 2000) : null }).subscribe({
      next: request => {
        this.sending = false;
        this.requests = [request, ...this.requests];
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

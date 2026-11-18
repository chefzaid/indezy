import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { SIGN_UP_OPEN } from '../../shared/utils/sign-up';

import { PublicTestimonialRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { TestimonialService } from '../../services/testimonial/testimonial.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';

/**
 * The page a client contact opens from a testimonial link, without an account (see docs/features.md#testimonials): who
 * asks, then the testimonial written once, its author agreeing to be named with it. The token comes after the # of the
 * link, so it never reaches a server log.
 */
@Component({
  selector: 'app-testimonial-writing',
  imports: [RouterLink, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule, PublicPageHeaderComponent],
  templateUrl: './testimonial-writing.component.html',
  styleUrls: ['./testimonial-writing.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TestimonialWritingComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly testimonials = inject(TestimonialService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly form = inject(FormBuilder).nonNullable.group({
    authorName: ['', [Validators.required, Validators.maxLength(120)]],
    authorTitle: ['', Validators.maxLength(160)],
    text: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(1500)]],
    consent: [false, Validators.requiredTrue]
  });

  /** Once written, its author may create an account, without having to. */
  readonly signUpOpen = SIGN_UP_OPEN;
  request?: PublicTestimonialRequest;
  isLoading = true;
  invalidLink = false;
  isSending = false;
  private token = '';

  ngOnInit(): void {
    this.token = (this.route.snapshot.fragment ?? '').trim();
    if (!this.token) {
      this.isLoading = false;
      this.invalidLink = true;
      return;
    }
    this.testimonials.open(this.token).subscribe({
      next: request => {
        this.request = request;
        this.form.patchValue({ authorName: request.recipientName });
        this.isLoading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.isLoading = false;
        this.invalidLink = true;
        this.changes.markForCheck();
      }
    });
  }

  send(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSending = true;
    const { authorName, authorTitle, text, consent } = this.form.getRawValue();
    this.testimonials.answer(this.token, { authorName: authorName.trim(), authorTitle: authorTitle.trim() || null, text: text.trim(), consent })
      .subscribe({
        next: request => {
          this.request = request;
          this.isSending = false;
          this.changes.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.isSending = false;
          this.notifications.error(error.status === 409 ? 'testimonialWriting.alreadyWritten' : 'testimonialWriting.sendError', 5000);
          this.changes.markForCheck();
        }
      });
  }
}

import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { BookingConfirmation, PublicBookingPage } from '../../models';
import { BookingService } from '../../services/booking/booking.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';

/**
 * The page a recruiter opens from a freelancer's booking link, without an account: the free times day by day, in the
 * visitor's time zone, then a short form to book one.
 */
@Component({
  selector: 'app-book',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule, PublicPageHeaderComponent],
  templateUrl: './book.component.html',
  styleUrls: ['./book.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class BookComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly bookings = inject(BookingService);

  page: PublicBookingPage | null = null;
  loading = true;
  chosen: string | null = null;
  sending = false;
  confirmation: BookingConfirmation | null = null;
  /** Why the last booking failed, if it did. */
  error: 'taken' | 'tooMany' | 'failed' | null = null;
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  private slug = '';

  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
    company: ['', Validators.maxLength(100)],
    message: ['', Validators.maxLength(500)]
  });

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.load();
  }

  choose(start: string): void {
    this.chosen = start;
    this.error = null;
  }

  book(): void {
    if (!this.chosen || this.form.invalid || this.sending) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.sending = true;
    this.bookings.book(this.slug, { start: this.chosen, name: value.name.trim(), email: value.email.trim(),
      company: value.company.trim() || null, message: value.message.trim() || null }).subscribe({
      next: confirmation => {
        this.sending = false;
        this.confirmation = confirmation;
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        this.error = error.status === 409 ? 'taken' : error.status === 429 ? 'tooMany' : 'failed';
        if (error.status === 409) {
          this.chosen = null;
          this.load();
        }
      }
    });
  }

  private load(): void {
    this.bookings.publicPage(this.slug).subscribe({
      next: page => {
        this.page = page;
        this.loading = false;
      },
      error: () => {
        this.page = null;
        this.loading = false;
      }
    });
  }
}

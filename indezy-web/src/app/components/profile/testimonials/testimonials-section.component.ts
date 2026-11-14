import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';

import { ContactDto, Testimonial } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TestimonialService } from '../../../services/testimonial/testimonial.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

/** A testimonial's link, built from its token once: the page an author opens without an account. */
interface AskedLink {
  recipient: string;
  url: string;
}

/**
 * The public page's testimonials (see docs/features.md#testimonials): ask a contact through a link, then show a
 * written testimonial on the public profile or keep it hidden.
 */
@Component({
  selector: 'app-testimonials-section',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './testimonials-section.component.html',
  styleUrls: ['./testimonials-section.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TestimonialsSectionComponent implements OnInit {
  private readonly testimonialService = inject(TestimonialService);
  private readonly contactService = inject(ContactService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly form = inject(FormBuilder).group({
    contactId: [null as number | null],
    recipientName: ['', Validators.maxLength(120)]
  });

  testimonials: Testimonial[] = [];
  contacts: ContactDto[] = [];
  asked: AskedLink | null = null;

  ngOnInit(): void {
    this.testimonialService.list().subscribe({
      next: testimonials => this.show(testimonials),
      error: () => this.notifications.error('profile.testimonials.loadError')
    });
    this.contactService.getForCurrentFreelance().subscribe({
      next: contacts => {
        this.contacts = contacts;
        this.changes.markForCheck();
      },
      error: () => this.contacts = []
    });
  }

  ask(): void {
    const { contactId, recipientName } = this.form.getRawValue();
    const name = recipientName?.trim() || null;
    if (!contactId && !name) {
      this.form.controls.recipientName.setErrors({ required: true });
      this.form.markAllAsTouched();
      return;
    }
    this.testimonialService.request(contactId ?? null, contactId ? null : name).subscribe({
      next: link => {
        this.asked = { recipient: link.testimonial.recipientName, url: `${globalThis.location.origin}/testimonial#${link.token}` };
        this.form.reset({ contactId: null, recipientName: '' });
        this.show([link.testimonial, ...this.testimonials]);
      },
      error: () => this.notifications.error('profile.testimonials.askError')
    });
  }

  copy(link: AskedLink): void {
    globalThis.navigator.clipboard.writeText(link.url).then(
      () => this.notifications.success('profile.testimonials.copied'),
      () => this.notifications.error('profile.testimonials.copyError'));
  }

  mailto(link: AskedLink): string {
    return `mailto:?body=${encodeURIComponent(link.url)}`;
  }

  setStatus(testimonial: Testimonial, status: 'APPROVED' | 'HIDDEN'): void {
    this.testimonialService.setStatus(testimonial.id, status).subscribe({
      next: saved => this.show(this.testimonials.map(each => each.id === saved.id ? saved : each)),
      error: () => this.notifications.error('profile.testimonials.saveError')
    });
  }

  remove(testimonial: Testimonial): void {
    this.confirm.confirm({ titleKey: 'profile.testimonials.deleteTitle', messageKey: 'profile.testimonials.deleteMessage',
      messageParams: { name: testimonial.authorName ?? testimonial.recipientName }, confirmKey: 'common.delete', danger: true }).pipe(
      filter(Boolean),
      switchMap(() => this.testimonialService.delete(testimonial.id))
    ).subscribe({
      next: () => this.show(this.testimonials.filter(each => each.id !== testimonial.id)),
      error: () => this.notifications.error('profile.testimonials.deleteError')
    });
  }

  private show(testimonials: Testimonial[]): void {
    this.testimonials = testimonials;
    this.changes.markForCheck();
  }
}

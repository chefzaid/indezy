import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';

import { ContactDto, Testimonial } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TestimonialService } from '../../../services/testimonial/testimonial.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { mailtoLink } from '../../../shared/utils/email-template';

/** The choice of someone outside the contacts, who is then asked by their names and email. */
const EXTERNAL = 'EXTERNAL';

/** A testimonial's link, built from its token once: the page an author opens without an account, and where it was emailed. */
interface AskedLink {
  recipient: string;
  url: string;
  email: string | null;
  emailed: boolean;
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
  /** The form's directive, reset with it after a send so its emptied fields show no error. */
  @ViewChild('askForm') private askForm?: FormGroupDirective;

  private readonly testimonialService = inject(TestimonialService);
  private readonly contactService = inject(ContactService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly translate = inject(TranslateService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  /** A contact, or an external person: their first and last names and the email their link goes to. */
  readonly form = inject(FormBuilder).nonNullable.group({
    contactId: [EXTERNAL as number | typeof EXTERNAL],
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    // An address the link can reach: a domain with its extension, as the server asks.
    email: ['', [Validators.required, Validators.email, Validators.pattern(/^[^@\s]+@[^@\s]+\.[^@\s]+$/), Validators.maxLength(254)]]
  });

  testimonials: Testimonial[] = [];
  contacts: ContactDto[] = [];
  asked: AskedLink | null = null;
  readonly EXTERNAL = EXTERNAL;
  asking = false;

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
    const { contactId, firstName, lastName, email } = this.form.getRawValue();
    const external = { firstName: firstName.trim(), lastName: lastName.trim(), email: email.trim() };
    if (contactId === EXTERNAL) {
      this.form.patchValue(external);
      if (this.form.controls.firstName.invalid || this.form.controls.lastName.invalid || this.form.controls.email.invalid) {
        this.form.markAllAsTouched();
        return;
      }
    }
    this.asking = true;
    this.testimonialService.request(contactId !== EXTERNAL ? { contactId } : { recipientFirstName: external.firstName,
      recipientLastName: external.lastName, recipientEmail: external.email }).subscribe({
      next: link => {
        this.asking = false;
        this.asked = { recipient: link.testimonial.recipientName, url: `${globalThis.location.origin}/testimonial#${link.token}`,
          email: link.testimonial.recipientEmail ?? null, emailed: link.emailed };
        this.resetAfterSubmit(this.askForm);
        this.show([link.testimonial, ...this.testimonials]);
      },
      error: (error: HttpErrorResponse) => {
        this.asking = false;
        this.notifications.error(error.status === 409 ? 'profile.testimonials.askLimit' : 'profile.testimonials.askError');
        this.changes.markForCheck();
      }
    });
  }

  copy(link: AskedLink): void {
    globalThis.navigator.clipboard.writeText(link.url).then(
      () => this.notifications.success('profile.testimonials.copied'),
      () => this.notifications.error('profile.testimonials.copyError'));
  }

  /** The request by email, to the person asked when their address was given, greeting them by name. */
  mailto(link: AskedLink): string {
    const params = { name: link.recipient, url: link.url };
    return mailtoLink(link.email, this.translate.instant('profile.testimonials.emailSubject'), this.translate.instant('profile.testimonials.emailBody', params));
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

  /** Empties the form after a send: its submitted state too, or its required fields show an error at once. */
  private resetAfterSubmit(directive: FormGroupDirective | undefined, value?: Parameters<typeof this.form.reset>[0]): void {
    if (directive) {
      directive.resetForm(value);
    } else {
      this.form.reset(value);
    }
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ContactDto, Testimonial } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TestimonialService } from '../../../services/testimonial/testimonial.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { TestimonialsSectionComponent } from './testimonials-section.component';

describe('TestimonialsSectionComponent', () => {
  let fixture: ComponentFixture<TestimonialsSectionComponent>;
  let testimonials: jasmine.SpyObj<TestimonialService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const received: Testimonial = { id: 1, recipientName: 'Léa Martin', status: 'RECEIVED', expiresAt: '2026-12-14T10:00:00Z',
    authorName: 'Léa Martin', authorTitle: 'CTO, Acme', text: 'Un consultant rigoureux.', receivedAt: '2026-11-14T10:00:00Z' };
  const asked: Testimonial = { id: 2, recipientName: 'Paul', status: 'REQUESTED', expiresAt: '2026-12-14T10:00:00Z' };

  function create(list: Testimonial[] | Error = [received, asked], contacts: ContactDto[] | Error = []): HTMLElement {
    testimonials = jasmine.createSpyObj('TestimonialService', ['list', 'request', 'setStatus', 'delete']);
    testimonials.list.and.returnValue(list instanceof Error ? throwError(() => list) : of(list));
    const contactService = jasmine.createSpyObj('ContactService', ['getForCurrentFreelance']);
    contactService.getForCurrentFreelance.and.returnValue(contacts instanceof Error ? throwError(() => contacts) : of(contacts));
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [TestimonialsSectionComponent, TranslateModule.forRoot()],
      providers: [{ provide: TestimonialService, useValue: testimonials }, { provide: ContactService, useValue: contactService },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: NotificationService, useValue: notifications }]
    });
    fixture = TestBed.createComponent(TestimonialsSectionComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the testimonials, a written one with its text and a button to show it', () => {
    const element = create();
    expect(element.querySelector('[data-testimonial="1"] blockquote')?.textContent).toContain('Un consultant rigoureux.');
    expect(element.querySelector('[data-testimonial="1"] [data-approve-testimonial]')).not.toBeNull();
    expect(element.querySelector('[data-testimonial="2"] [data-approve-testimonial]')).toBeNull();
  });

  it('asks a contact, or an external person by their names and email, and builds the link from its token', () => {
    create([], [{ id: 7, firstName: 'Léa', lastName: 'Martin', clientId: 3 } as ContactDto]);
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    // An external person is the default: their names and email are asked, each required.
    expect(element.querySelector('[data-testimonial-external]')).not.toBeNull();
    component.ask();
    expect(testimonials.request).not.toHaveBeenCalled();
    component.form.patchValue({ firstName: 'Marie', lastName: 'Durand', email: 'not-an-email' });
    component.ask();
    expect(testimonials.request).not.toHaveBeenCalled();

    testimonials.request.and.returnValue(of({ token: 'abc', emailed: true, testimonial: { ...asked, id: 3, recipientName: 'Marie Durand',
      recipientEmail: 'marie@acme.example' } }));
    component.form.patchValue({ firstName: ' Marie ', lastName: 'Durand', email: ' marie@acme.example ' });
    component.ask();
    expect(testimonials.request).toHaveBeenCalledWith({ recipientFirstName: 'Marie', recipientLastName: 'Durand',
      recipientEmail: 'marie@acme.example' });
    expect(component.asked).toEqual({ recipient: 'Marie Durand', url: `${location.origin}/testimonial#abc`, email: 'marie@acme.example',
      emailed: true });
    fixture.detectChanges();
    expect(element.querySelector('[data-testimonial-emailed]')?.textContent).toContain('profile.testimonials.emailed');
    // To the person asked when their address was given, with a subject and a greeting.
    expect(component.mailto(component.asked!)).toMatch(/^mailto:marie@acme\.example\?subject=profile\.testimonials\.emailSubject/);
    expect(component.mailto({ ...component.asked!, email: null })).toMatch(/^mailto:\?subject=/);

    // A contact: no names asked, and an email that could not go says so.
    testimonials.request.and.returnValue(of({ token: 'def', emailed: false, testimonial: { ...asked, id: 4, recipientName: 'Léa Martin',
      recipientEmail: 'lea@acme.example' } }));
    component.form.patchValue({ contactId: 7 });
    fixture.detectChanges();
    expect(element.querySelector('[data-testimonial-external]')).toBeNull();
    component.ask();
    expect(testimonials.request).toHaveBeenCalledWith({ contactId: 7 });
    fixture.detectChanges();
    expect(element.querySelector('[data-testimonial-not-emailed]')).not.toBeNull();

    testimonials.request.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.form.patchValue({ contactId: 7 });
    component.ask();
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.askLimit');
    testimonials.request.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.ask();
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.askError');
    expect(component.asking).toBeFalse();
  });

  it('shows, hides and deletes a testimonial once confirmed', () => {
    create();
    const component = fixture.componentInstance;
    testimonials.setStatus.and.returnValue(of({ ...received, status: 'APPROVED' }));
    component.setStatus(received, 'APPROVED');
    expect(component.testimonials[0].status).toBe('APPROVED');
    testimonials.setStatus.and.returnValue(throwError(() => new Error('down')));
    component.setStatus(received, 'HIDDEN');
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.saveError');

    confirm.confirm.and.returnValue(of(true));
    testimonials.delete.and.returnValue(of(undefined));
    component.remove(asked);
    expect(component.testimonials.map(each => each.id)).toEqual([1]);
    testimonials.delete.and.returnValue(throwError(() => new Error('down')));
    component.remove(received);
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.deleteError');
  });

  it('copies the link, and says when the list cannot be read', async () => {
    create(new Error('down'), new Error('down'));
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.loadError');
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    fixture.componentInstance.copy({ recipient: 'Paul', url: 'https://x/testimonial#t', email: null, emailed: false });
    await Promise.resolve();
    expect(notifications.success).toHaveBeenCalledWith('profile.testimonials.copied');
  });
});

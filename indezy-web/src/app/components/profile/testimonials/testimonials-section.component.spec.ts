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

  it('asks a contact or someone named, and builds the link from its token', () => {
    create([], [{ id: 7, firstName: 'Léa', lastName: 'Martin', clientId: 3 } as ContactDto]);
    const component = fixture.componentInstance;
    component.ask();
    expect(testimonials.request).not.toHaveBeenCalled();

    testimonials.request.and.returnValue(of({ token: 'abc', testimonial: { ...asked, id: 3, recipientName: 'Léa Martin' } }));
    component.form.patchValue({ contactId: 7, recipientName: 'ignored' });
    component.ask();
    expect(testimonials.request).toHaveBeenCalledWith(7, null);
    expect(component.asked).toEqual({ recipient: 'Léa Martin', url: `${location.origin}/testimonial#abc` });
    expect(component.mailto(component.asked!)).toContain(encodeURIComponent('/testimonial#abc'));

    component.form.patchValue({ recipientName: ' Paul ' });
    component.ask();
    expect(testimonials.request).toHaveBeenCalledWith(null, 'Paul');
    testimonials.request.and.returnValue(throwError(() => new Error('down')));
    component.form.patchValue({ recipientName: 'Paul' });
    component.ask();
    expect(notifications.error).toHaveBeenCalledWith('profile.testimonials.askError');
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
    fixture.componentInstance.copy({ recipient: 'Paul', url: 'https://x/testimonial#t' });
    await Promise.resolve();
    expect(notifications.success).toHaveBeenCalledWith('profile.testimonials.copied');
  });
});

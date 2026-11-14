import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PublicTestimonialRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { TestimonialService } from '../../services/testimonial/testimonial.service';
import { TestimonialWritingComponent } from './testimonial-writing.component';

describe('TestimonialWritingComponent', () => {
  let fixture: ComponentFixture<TestimonialWritingComponent>;
  let testimonials: jasmine.SpyObj<TestimonialService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const open: PublicTestimonialRequest = { freelancerName: 'Alice Martin', recipientName: 'Léa Martin', answered: false };

  function create(fragment: string | null, answer: PublicTestimonialRequest | Error = open): HTMLElement {
    testimonials = jasmine.createSpyObj('TestimonialService', ['open', 'answer']);
    testimonials.open.and.returnValue(answer instanceof Error ? throwError(() => answer) : of(answer));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [TestimonialWritingComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: TestimonialService, useValue: testimonials },
        { provide: NotificationService, useValue: notifications }, { provide: ActivatedRoute, useValue: { snapshot: { fragment } } }]
    });
    fixture = TestBed.createComponent(TestimonialWritingComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says a link without a token or an unknown one is not valid', () => {
    expect(create(null).querySelector('[data-testimonial-invalid]')).not.toBeNull();
    expect(testimonials.open).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    expect(create('bad', new Error('404')).querySelector('[data-testimonial-invalid]')).not.toBeNull();
  });

  it('writes the testimonial once its author agrees to be named, then thanks them', () => {
    create(' token ');
    const component = fixture.componentInstance;
    expect(testimonials.open).toHaveBeenCalledWith('token');
    expect(component.form.value.authorName).toBe('Léa Martin');
    component.form.patchValue({ text: 'Un consultant rigoureux et très fiable.' });
    component.send();
    expect(testimonials.answer).not.toHaveBeenCalled();

    testimonials.answer.and.returnValue(of({ ...open, answered: true }));
    component.form.patchValue({ consent: true, authorTitle: ' ' });
    component.send();
    expect(testimonials.answer).toHaveBeenCalledWith('token', { authorName: 'Léa Martin', authorTitle: null,
      text: 'Un consultant rigoureux et très fiable.', consent: true });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testimonial-thanks]')).not.toBeNull();
  });

  it('says when the testimonial was already written or could not be sent', () => {
    create('token');
    const component = fixture.componentInstance;
    component.form.patchValue({ text: 'Un consultant rigoureux et très fiable.', consent: true });
    testimonials.answer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('testimonialWriting.alreadyWritten', 5000);
    testimonials.answer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('testimonialWriting.sendError', 5000);
    expect(component.isSending).toBeFalse();
  });
});

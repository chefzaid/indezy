import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { PublicBookingPage } from '../../models';
import { BookingService } from '../../services/booking/booking.service';
import { BookComponent } from './book.component';
import en from '../../../assets/i18n/en.json';

describe('BookComponent', () => {
  let service: jasmine.SpyObj<BookingService>;
  const page: PublicBookingPage = {
    freelancer: 'Alice Martin', title: 'Call with Alice', durationMinutes: 30, timeZone: 'Europe/Paris',
    days: [{ date: '2026-11-02', starts: ['2026-11-02T09:00:00Z', '2026-11-02T09:45:00Z'] }]
  };

  function create(answer: Observable<PublicBookingPage> = of(page)): { element: HTMLElement; detect: () => void } {
    service = jasmine.createSpyObj('BookingService', ['publicPage', 'book']);
    service.publicPage.and.returnValue(answer);
    TestBed.configureTestingModule({
      imports: [BookComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, provideRouter([]), { provide: BookingService, useValue: service },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ slug: 'abc123' }) } } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(BookComponent);
    fixture.detectChanges();
    return { element: fixture.nativeElement as HTMLElement, detect: () => fixture.detectChanges() };
  }

  function fill(element: HTMLElement, name: string, value: string): void {
    const input = element.querySelector(`[formControlName="${name}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('books a chosen time with the visitor\'s details', () => {
    const { element, detect } = create();
    expect(service.publicPage).toHaveBeenCalledWith('abc123');
    expect(element.textContent).toContain('Book a 30-minute call with Alice Martin.');
    expect(element.querySelectorAll('[data-book-start]').length).toBe(2);
    expect(element.querySelector('[data-book-form]')).toBeNull();

    (element.querySelector('[data-book-start="2026-11-02T09:45:00Z"]') as HTMLButtonElement).click();
    detect();
    expect(element.querySelector('[data-book-start="2026-11-02T09:45:00Z"]')!.getAttribute('aria-pressed')).toBe('true');
    (element.querySelector('[data-book-submit]') as HTMLButtonElement).click();
    expect(service.book).not.toHaveBeenCalled();

    fill(element, 'name', ' Bob Recruiter ');
    fill(element, 'email', 'bob@esn.example');
    service.book.and.returnValue(of({ startsAt: '2026-11-02T09:45:00Z', endsAt: '2026-11-02T10:15:00Z', freelancer: 'Alice Martin' }));
    (element.querySelector('[data-book-submit]') as HTMLButtonElement).click();
    detect();
    expect(service.book).toHaveBeenCalledWith('abc123', { start: '2026-11-02T09:45:00Z', name: 'Bob Recruiter', email: 'bob@esn.example',
      company: null, message: null });
    expect(element.querySelector('[data-book-confirmed]')!.textContent).toContain('Alice Martin expects your call');
  });

  it('shows the times again when the chosen one was just taken, and says when there were too many bookings', () => {
    const { element, detect } = create();
    (element.querySelector('[data-book-start]') as HTMLButtonElement).click();
    detect();
    fill(element, 'name', 'Bob');
    fill(element, 'email', 'bob@esn.example');
    fill(element, 'company', ' ESN ');
    service.book.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    (element.querySelector('[data-book-submit]') as HTMLButtonElement).click();
    detect();
    expect(element.querySelector('[data-book-error]')!.textContent).toContain('This time was just taken');
    expect(element.querySelector('[data-book-form]')).toBeNull();
    expect(service.publicPage).toHaveBeenCalledTimes(2);

    (element.querySelector('[data-book-start]') as HTMLButtonElement).click();
    service.book.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    detect();
    (element.querySelector('[data-book-submit]') as HTMLButtonElement).click();
    detect();
    expect(service.book.calls.mostRecent().args[1].company).toBe('ESN');
    expect(element.querySelector('[data-book-error]')!.textContent).toContain('Too many bookings');
    service.book.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    (element.querySelector('[data-book-submit]') as HTMLButtonElement).click();
    detect();
    expect(element.querySelector('[data-book-error]')!.textContent).toContain('could not be booked');
  });

  it('says a link turned off or replaced does not work, and when no time is free', () => {
    expect(create(throwError(() => new HttpErrorResponse({ status: 404 }))).element.querySelector('[data-book-invalid]')).not.toBeNull();
    TestBed.resetTestingModule();
    expect(create(of({ ...page, days: [] })).element.textContent).toContain('No free time in the coming days.');
  });
});

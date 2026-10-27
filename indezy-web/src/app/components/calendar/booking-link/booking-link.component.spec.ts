import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Booking, BookingPage } from '../../../models';
import { BookingService } from '../../../services/booking/booking.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { BookingLinkComponent } from './booking-link.component';
import en from '../../../../assets/i18n/en.json';

describe('BookingLinkComponent', () => {
  let service: jasmine.SpyObj<BookingService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  const page: BookingPage = { slug: 'abc123', settings: { enabled: true, title: 'Call', durationMinutes: 30, bufferMinutes: 15,
    minNoticeHours: 24, maxDaysAhead: 21, windows: [{ dayOfWeek: 'MONDAY', start: '09:00', end: '12:00' }] } };
  const soon = new Date(Date.now() + 86_400_000).toISOString();
  const later = new Date(Date.now() + 88_200_000).toISOString();
  const call: Booking = { id: 3, startsAt: soon, endsAt: later, name: 'Bob', email: 'bob@esn.example', company: 'ESN', message: 'Mission Java',
    cancelledAt: null };
  const past: Booking = { ...call, id: 2, startsAt: '2026-01-05T09:00:00Z', endsAt: '2026-01-05T09:30:00Z' };

  function create(answer = of(page), calls = of([past, call])): { component: BookingLinkComponent; element: HTMLElement; detect: () => void } {
    service = jasmine.createSpyObj('BookingService', ['page', 'save', 'renewLink', 'list', 'cancel']);
    service.page.and.returnValue(answer);
    service.list.and.returnValue(calls);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    TestBed.configureTestingModule({
      imports: [BookingLinkComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: BookingService, useValue: service }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: Clipboard, useValue: clipboard }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(BookingLinkComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement, detect: () => fixture.detectChanges() };
  }

  it('shows the link to share and the calls to come, and copies the link', () => {
    const { element } = create();
    expect(element.querySelector('[data-booking-url]')!.textContent).toBe(`${location.origin}/book/abc123`);
    expect(element.textContent).toContain('30-minute calls, 1 day(s) a week.');
    expect(element.querySelector('[data-booking="2"]')).toBeNull();
    expect(element.querySelector('[data-booking="3"]')!.textContent).toContain('Mission Java');

    clipboard.copy.and.returnValue(true);
    (element.querySelector('[data-copy-booking-link]') as HTMLButtonElement).click();
    expect(clipboard.copy).toHaveBeenCalledWith(`${location.origin}/book/abc123`);
    expect(notifications.success).toHaveBeenCalledWith('booking.copied');
  });

  it('saves the settings chosen in the dialog', () => {
    const { component, element, detect } = create(of({ slug: null, settings: { ...page.settings, enabled: false, windows: [] } }), of([]));
    expect(element.querySelector('[data-booking-off]')).not.toBeNull();
    expect(element.querySelector('[data-booking-settings]')!.textContent).toContain('Set up the link');
    expect(element.textContent).toContain('No call booked yet.');

    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    component.edit();
    expect(service.save).not.toHaveBeenCalled();
    dialog.open.and.returnValue({ afterClosed: () => of(page.settings) } as never);
    service.save.and.returnValue(of(page));
    component.edit();
    detect();
    expect(service.save).toHaveBeenCalledWith(page.settings);
    expect(element.querySelector('[data-booking-url]')).not.toBeNull();
    service.save.and.returnValue(throwError(() => new Error('400')));
    component.edit();
    expect(notifications.error).toHaveBeenCalledWith('booking.saveError');
  });

  it('gives the link a new address once confirmed', () => {
    const { component } = create();
    confirm.confirm.and.returnValue(of(false));
    component.renew();
    expect(service.renewLink).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    service.renewLink.and.returnValue(of({ ...page, slug: 'new456' }));
    component.renew();
    expect(component.url).toContain('/book/new456');
    expect(notifications.success).toHaveBeenCalledWith('booking.renewed');
    service.renewLink.and.returnValue(throwError(() => new Error('500')));
    component.renew();
    expect(notifications.error).toHaveBeenCalledWith('booking.saveError');
  });

  it('cancels a call once confirmed and tells the calendar', () => {
    const { component, element, detect } = create();
    const changed = spyOn(component.changed, 'emit');
    confirm.confirm.and.returnValue(of(false));
    (element.querySelector('[data-booking="3"] button') as HTMLButtonElement).click();
    expect(service.cancel).not.toHaveBeenCalled();
    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageParams: { name: 'Bob' } }));

    confirm.confirm.and.returnValue(of(true));
    service.cancel.and.returnValue(of({ ...call, cancelledAt: '2026-10-27T10:00:00Z' }));
    component.cancel(call);
    detect();
    expect(service.cancel).toHaveBeenCalledWith(3);
    expect(changed).toHaveBeenCalled();
    expect(element.querySelector('[data-booking="3"]')).toBeNull();
    service.cancel.and.returnValue(throwError(() => new Error('404')));
    component.cancel(call);
    expect(notifications.error).toHaveBeenCalledWith('booking.saveError');
  });

  it('says when the link or the calls could not be read', () => {
    const { component } = create(throwError(() => new Error('500')), throwError(() => new Error('500')));
    expect(component.page).toBeNull();
    expect(component.url).toBeNull();
    expect(notifications.error).toHaveBeenCalledWith('booking.loadError');
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });
});

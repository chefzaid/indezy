import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { Booking, InterviewStepDto, StepStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { BookingService } from '../../services/booking/booking.service';
import { NotificationService } from '../../services/notification/notification.service';
import { CalendarComponent } from './calendar.component';
import en from '../../../assets/i18n/en.json';

describe('CalendarComponent', () => {
  let steps: jasmine.SpyObj<InterviewStepService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigate: jasmine.Spy;
  let bookings: jasmine.SpyObj<BookingService>;
  const planned: InterviewStepDto[] = [
    { id: 1, title: 'Technical test', status: StepStatus.PLANNED, date: '2026-10-20T14:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 2, title: 'Call', status: StepStatus.VALIDATED, date: '2026-10-20T09:30:00', projectId: 5, projectRole: 'Ops' },
    { id: 3, title: 'Client', status: StepStatus.PLANNED, date: '2026-10-20T16:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 4, title: 'Final', status: StepStatus.PLANNED, date: '2026-10-20T17:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 5, title: 'Next month', status: StepStatus.TO_PLAN, date: '2026-11-03T10:00:00', projectId: 6, projectRole: 'Data' }
  ];

  function create(month: string | null = '2026-10', user: { id: number } | null = { id: 7 },
                  list = of(planned), calls: Observable<Booking[]> = of([])): ComponentFixture<CalendarComponent> {
    bookings = jasmine.createSpyObj('BookingService', ['page', 'list']);
    bookings.page.and.returnValue(of({ slug: null, settings: { enabled: false, title: '', durationMinutes: 30, bufferMinutes: 15,
      minNoticeHours: 24, maxDaysAhead: 21, windows: [] } }));
    bookings.list.and.returnValue(calls);
    steps = jasmine.createSpyObj('InterviewStepService', ['getByFreelanceIdAndStatus']);
    steps.getByFreelanceIdAndStatus.and.returnValue(list);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    params = new BehaviorSubject(convertToParamMap(month ? { month } : {}));
    TestBed.configureTestingModule({
      imports: [CalendarComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: params.asObservable() } },
        { provide: InterviewStepService, useValue: steps },
        { provide: BookingService, useValue: bookings },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(CalendarComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(element: Element): string {
    return element.textContent!.replace(/\s+/g, ' ').trim();
  }

  it('shows the month of the address, each day\'s steps by time and the rest counted', () => {
    const fixture = create();
    const element = fixture.nativeElement as HTMLElement;

    expect(steps.getByFreelanceIdAndStatus).toHaveBeenCalledWith(7);
    expect(text(element.querySelector('.month-name')!)).toBe('October 2026');
    expect(element.querySelectorAll('.week').length).toBe(5);
    const day = element.querySelector('[data-calendar-day="2026-10-20"]')!;
    expect(day.querySelector('.cdk-visually-hidden')!.textContent).toBe('Tuesday, October 20, 2026: 4 step(s)');
    expect(Array.from(day.querySelectorAll('.day-step')).map(text)).toEqual(['09:30 Call', '14:00 Technical test', '16:00 Client']);
    expect(text(day.querySelector('.day-more')!)).toBe('+1');
    expect(element.querySelectorAll('.day.out').length).toBe(4);
  });

  it('lists a picked day\'s steps in full with links to their opportunities', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.select(component.weeks.flat().find(day => day.key === '2026-10-20')!);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const links = Array.from(element.querySelectorAll<HTMLAnchorElement>('.detail-step'));
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/projects/5', '/projects/4', '/projects/4', '/projects/4']);
    expect(text(links[0])).toBe('09:30CallOps · Validated');

    component.select(component.weeks.flat().find(day => day.key === '2026-10-21')!);
    fixture.detectChanges();
    expect(text(element.querySelector('.day-detail')!)).toContain('Nothing planned this day');
  });

  it('moves between months through the address, keeping the picked day when it is still shown', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.go(1);
    expect(navigate.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ queryParams: { month: '2026-11' } }));
    component.go(-1);
    expect(navigate.calls.mostRecent().args[1].queryParams).toEqual({ month: '2026-09' });

    component.select(component.weeks.flat().find(day => day.key === '2026-10-20')!);
    params.next(convertToParamMap({ month: '2026-10' }));
    expect(component.selected!.key).toBe('2026-10-20');
    params.next(convertToParamMap({ month: '2026-11' }));
    expect(component.selected!.key).toBe('2026-11-01');
    expect(component.weeks.flat().find(day => day.key === '2026-11-03')!.steps.map(step => step.id)).toEqual([5]);
  });

  it('opens on the current month and today, and goes back to it', () => {
    const component = create(null).componentInstance;
    const today = new Date();

    expect(component.month.getMonth()).toBe(today.getMonth());
    expect(component.selected!.key).toBe(component.todayKey);
    component.go();
    expect(navigate.calls.mostRecent().args[1].queryParams.month).toBe(component.todayKey.substring(0, 7));
  });

  it('loads nothing without an account and says when the steps cannot be loaded', () => {
    create('2026-10', null);
    expect(steps.getByFreelanceIdAndStatus).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    create('2026-10', { id: 7 }, throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('calendar.loadError');
  });

  it('shows the calls booked through the link with the steps, counting them in the day', () => {
    const call = (id: number, hour: number, cancelledAt: string | null = null): Booking => ({ id, name: `Recruiter ${id}`,
      startsAt: new Date(2026, 9, 20, hour).toISOString(), endsAt: new Date(2026, 9, 20, hour, 30).toISOString(),
      email: 'r@esn.example', company: id === 8 ? 'ESN' : null, message: null, cancelledAt });
    const fixture = create('2026-10', { id: 7 }, of(planned), of([call(8, 11), call(9, 12), call(10, 13, '2026-10-19T08:00:00Z')]));
    const element = fixture.nativeElement as HTMLElement;
    const day = element.querySelector('[data-calendar-day="2026-10-20"]')!;

    expect(day.querySelectorAll('.day-booking').length).toBe(0);
    expect(text(day.querySelector('.day-more')!)).toBe('+3');
    fixture.componentInstance.select(fixture.componentInstance.weeks.flat().find(item => item.key === '2026-10-20')!);
    fixture.detectChanges();
    expect(text(element.querySelector('[data-calendar-booking="8"]')!)).toContain('Call with Recruiter 8ESN');
    expect(element.querySelector('[data-calendar-booking="9"]')!.textContent).toContain('r@esn.example');
    expect(element.querySelector('[data-calendar-booking="10"]')).toBeNull();
  });

  it('shows a call in a day with room left, and says when the calls cannot be loaded', () => {
    const lone: Booking = { id: 4, name: 'Bob', startsAt: new Date(2026, 9, 6, 10).toISOString(), endsAt: new Date(2026, 9, 6, 10, 30).toISOString(),
      email: 'bob@esn.example', company: null, message: null, cancelledAt: null };
    const element = create('2026-10', { id: 7 }, of(planned), of([lone])).nativeElement as HTMLElement;
    expect(text(element.querySelector('[data-calendar-day="2026-10-06"] .day-booking')!)).toBe('10:00 Bob');

    TestBed.resetTestingModule();
    create('2026-10', { id: 7 }, of(planned), throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('calendar.loadError');
  });

  it('lists the planned steps and calls to come in the upcoming view, and switches views through the address', () => {
    const later: InterviewStepDto[] = [
      { id: 11, title: 'Client interview', status: StepStatus.PLANNED, date: '2099-01-10T10:00:00', projectId: 4, projectRole: 'Java developer' },
      { id: 12, title: 'Debrief', status: StepStatus.VALIDATED, date: '2099-01-05T10:00:00', projectId: 4, projectRole: 'Java developer' },
      { id: 13, title: 'Past', status: StepStatus.PLANNED, date: '2020-01-05T10:00:00', projectId: 5, projectRole: 'Ops' }
    ];
    const call: Booking = { id: 21, name: 'Recruiter', startsAt: new Date(2099, 0, 8, 11).toISOString(),
      endsAt: new Date(2099, 0, 8, 11, 30).toISOString(), email: 'r@esn.example', company: 'ESN', message: null, cancelledAt: null };
    const fixture = create('2026-10', { id: 7 }, of(later), of([call]));
    const component = fixture.componentInstance;
    const element = fixture.nativeElement as HTMLElement;
    expect(component.view).toBe('month');
    expect(element.querySelector('[data-calendar-upcoming]')).toBeNull();

    params.next(convertToParamMap({ month: '2026-10', view: 'upcoming' }));
    fixture.detectChanges();
    expect(element.querySelector('.month-grid')).toBeNull();
    expect(element.querySelector('.month-nav')).toBeNull();
    const rows = Array.from(element.querySelectorAll('[data-calendar-upcoming] .detail-step'));
    expect(rows.length).toBe(2);
    expect(text(rows[0])).toContain('Call with Recruiter');
    expect(text(rows[0])).toContain('ESN');
    expect(text(rows[1])).toContain('Client interview');
    expect(rows[1].getAttribute('href')).toBe('/projects/4');

    component.show('month');
    expect(navigate.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ queryParams: { view: null },
      queryParamsHandling: 'merge' }));
    component.show('upcoming');
    expect(navigate.calls.mostRecent().args[1].queryParams).toEqual({ view: 'upcoming' });
  });

  it('says when nothing is to come', () => {
    const fixture = create('2026-10', { id: 7 }, of([]));
    params.next(convertToParamMap({ view: 'upcoming' }));
    fixture.detectChanges();

    expect(text(fixture.nativeElement.querySelector('[data-calendar-upcoming]'))).toContain('No upcoming scheduled step or call');
  });
});

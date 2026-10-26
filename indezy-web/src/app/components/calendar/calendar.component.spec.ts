import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { InterviewStepDto, StepStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { NotificationService } from '../../services/notification/notification.service';
import { CalendarComponent } from './calendar.component';
import en from '../../../assets/i18n/en.json';

describe('CalendarComponent', () => {
  let steps: jasmine.SpyObj<InterviewStepService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigate: jasmine.Spy;
  const planned: InterviewStepDto[] = [
    { id: 1, title: 'Technical test', status: StepStatus.PLANNED, date: '2026-10-20T14:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 2, title: 'Call', status: StepStatus.VALIDATED, date: '2026-10-20T09:30:00', projectId: 5, projectRole: 'Ops' },
    { id: 3, title: 'Client', status: StepStatus.PLANNED, date: '2026-10-20T16:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 4, title: 'Final', status: StepStatus.PLANNED, date: '2026-10-20T17:00:00', projectId: 4, projectRole: 'Java developer' },
    { id: 5, title: 'Next month', status: StepStatus.TO_PLAN, date: '2026-11-03T10:00:00', projectId: 6, projectRole: 'Data' }
  ];

  function create(month: string | null = '2026-10', user: { id: number } | null = { id: 7 },
                  list = of(planned)): ComponentFixture<CalendarComponent> {
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
    const day = Array.from(element.querySelectorAll('.day')).find(cell => cell.getAttribute('aria-label')!.startsWith('Tuesday, October 20, 2026'))!;
    expect(day.getAttribute('aria-label')).toContain('4 step(s)');
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
});

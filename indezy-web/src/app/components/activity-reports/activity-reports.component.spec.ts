import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ProjectDto, ProjectStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { ActivityReportCardComponent } from '../projects/activity-report-card/activity-report-card.component';
import { MissionEndActionComponent } from '../projects/mission-end-action/mission-end-action.component';
import { ActivityReportsComponent, activeMissions } from './activity-reports.component';

@Component({ selector: 'app-activity-report-card', template: '' })
class ActivityReportCardStubComponent {
  @Input() project!: ProjectDto;
}

@Component({ selector: 'app-mission-end-action', template: '' })
class MissionEndActionStubComponent {
  @Input() project!: ProjectDto;
  @Output() readonly changed = new EventEmitter<ProjectDto>();
}

const mission = (id: number, overrides: Partial<ProjectDto> = {}): ProjectDto =>
  ({ id, role: `Mission ${id}`, dailyRate: 600, status: ProjectStatus.WON, clientName: 'Airbus', ...overrides }) as ProjectDto;

describe('activeMissions', () => {
  const today = new Date(2026, 9, 28);

  it('keeps the won missions started by the end of the month and not ended before it began, earliest first', () => {
    const projects = [
      mission(1, { startDate: '2026-10-20' }),
      mission(2, { startDate: '2026-01-05', durationInMonths: 3 }),
      mission(3, { startDate: '2026-11-02' }),
      mission(4, { startDate: '2026-02-01', endedOn: '2026-10-15' }),
      mission(5, { startDate: '2026-02-01', endedOn: '2026-09-30' }),
      mission(6, { startDate: '2026-02-01', archivedAt: '2026-10-01T03:30:00' }),
      mission(7, { startDate: '2026-02-01', status: ProjectStatus.OFFER }),
      mission(8)
    ];

    // Without a start, past its planned end without being ended, ended this month, and started this month.
    expect(activeMissions(projects, today).map(project => project.id)).toEqual([8, 2, 4, 1]);
  });
});

describe('ActivityReportsComponent', () => {
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(list: ProjectDto[] | Error, user: { id: number } | null = { id: 7 }): ComponentFixture<ActivityReportsComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['getByFreelanceId']);
    projects.getByFreelanceId.and.returnValue(list instanceof Error ? throwError(() => list) : of(list));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [ActivityReportsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ProjectService, useValue: projects },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    TestBed.overrideComponent(ActivityReportsComponent, {
      remove: { imports: [ActivityReportCardComponent, MissionEndActionComponent] },
      add: { imports: [ActivityReportCardStubComponent, MissionEndActionStubComponent] }
    });
    const fixture = TestBed.createComponent(ActivityReportsComponent);
    fixture.detectChanges();
    return fixture;
  }

  const page = (fixture: ComponentFixture<ActivityReportsComponent>) => fixture.nativeElement as HTMLElement;

  it('lists each mission in progress with its activity report and its end button, under the page header', () => {
    const current = mission(1, { startDate: '2026-01-05' });
    const fixture = create([current, mission(2, { status: ProjectStatus.CONTACT })]);

    expect(projects.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(page(fixture).querySelector('h1')!.textContent).toContain('activityReportsPage.title');
    const sections = page(fixture).querySelectorAll('[data-active-mission]');
    expect(sections.length).toBe(1);
    expect(sections[0].querySelector('h2 a')!.getAttribute('href')).toBe('/projects/1');
    expect(sections[0].textContent).toContain('Airbus');
    const card = fixture.debugElement.query(debug => debug.componentInstance instanceof ActivityReportCardStubComponent);
    expect((card.componentInstance as ActivityReportCardStubComponent).project).toEqual(current);
  });

  it('points out a mission past its planned end that was not ended', () => {
    const fixture = create([mission(1, { startDate: '2025-01-06', durationInMonths: 3 }), mission(2, { startDate: '2026-01-05' })]);

    expect(page(fixture).querySelector('[data-active-mission="1"] [data-planned-end-passed]')).not.toBeNull();
    expect(page(fixture).querySelector('[data-active-mission="2"] [data-planned-end-passed]')).toBeNull();
  });

  it('keeps a mission ended from the page listed, with its end', () => {
    const fixture = create([mission(1, { startDate: '2026-01-05' })]);
    const action = fixture.debugElement.query(debug => debug.componentInstance instanceof MissionEndActionStubComponent);

    (action.componentInstance as MissionEndActionStubComponent).changed.emit(mission(1, { startDate: '2026-01-05', endedOn: '2026-10-28' }));
    fixture.detectChanges();

    expect(fixture.componentInstance.missions[0].endedOn).toBe('2026-10-28');
    expect(page(fixture).querySelector('[data-active-mission="1"]')!.textContent).toContain('activityReportsPage.endedOn');
  });

  it('says there is no mission in progress', () => {
    const fixture = create([mission(1, { status: ProjectStatus.LOST })]);

    expect(page(fixture).querySelector('[data-no-active-mission]')).not.toBeNull();
    expect(page(fixture).querySelector('[data-no-active-mission] a')!.getAttribute('href')).toBe('/projects');
  });

  it('says when the missions could not be loaded, and loads nothing without a user', () => {
    const failing = create(new Error('down'));
    expect(notifications.error).toHaveBeenCalledWith('activityReportsPage.loadError');
    expect(failing.componentInstance.isLoading).toBeFalse();

    TestBed.resetTestingModule();
    const signedOut = create([], null);
    expect(projects.getByFreelanceId).not.toHaveBeenCalled();
    expect(signedOut.componentInstance.isLoading).toBeFalse();
  });
});

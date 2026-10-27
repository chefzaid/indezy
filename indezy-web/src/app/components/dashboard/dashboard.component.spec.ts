import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { NotificationService } from '../../services/notification/notification.service';
import { DashboardWidget, UserPreferences } from '../../models/user-management.models';
import { TranslateModule } from '@ngx-translate/core';
import { Chart } from 'chart.js';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DashboardStatsDto, ProjectDto, User } from '../../models';
import { Season } from '../../models/season.models';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectService } from '../../services/project/project.service';
import { KanbanBoardComponent } from '../kanban-board/kanban-board.component';
import { SeasonPickerComponent } from '../seasons/season-picker/season-picker.component';
import { DashboardAnalyticsComponent } from './dashboard-analytics/dashboard-analytics.component';
import { DashboardOffersComponent } from './dashboard-offers/dashboard-offers.component';
import { DashboardComponent } from './dashboard.component';

@Component({ selector: 'app-kanban-board', template: 'kanban board' })
class KanbanBoardStubComponent {
  @Input() seasonId: number | null = null;
}

@Component({ selector: 'app-season-picker', template: '' })
class SeasonPickerStubComponent {
  @Input() freelanceId!: number;
  @Input() defaultTargetDailyRate: number | null = null;
  @Output() readonly selectionChange = new EventEmitter<Season | null>();
}

@Component({ selector: 'app-dashboard-offers', template: '' })
class DashboardOffersStubComponent {}

const DAY = 86_400_000;

function isoDaysAgo(days: number): string {
  const date = new Date(Date.now() - days * DAY);
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`;
}

function stats(overrides: Partial<DashboardStatsDto> = {}): DashboardStatsDto {
  return {
    totalProjects: 12, averageDailyRate: 600, totalEstimatedRevenue: 120000, forecastRevenue: 80000, activeProjects: 4,
    wonProjects: 3, lostProjects: 1, totalBenchDays: 0, benchPeriods: 0, estimatedBenchCost: 0,
    projectsByStatus: { IDEA: 2, WON: 3, UNKNOWN: 1 }, projectsByWorkMode: { REMOTE: 5, HYBRID: 2 },
    lostReasonsBreakdown: { RATE: 1, TIMING: 3, OTHER: 0 }, dailyRateRanges: [], sourceRoi: [], dailyRateEvolution: [],
    conversionFunnel: [], funnelBySource: [], funnelByClientType: [], funnelByEsn: [], missionsEndingSoon: [],
    staleOpportunities: [], upcomingRenewals: [], onThisDay: [], dormantContacts: [], skillTrends: [], processDurations: [],
    activityHeatmap: [], ...overrides
  };
}

function project(id: number, seasonId?: number, overrides: Partial<ProjectDto> = {}): ProjectDto {
  return { id, seasonId, role: `Role ${id}`, clientName: `Client ${id}`, dailyRate: 500 + id, ...overrides } as ProjectDto;
}

describe('DashboardComponent', () => {
  let projects: jasmine.SpyObj<ProjectService>;
  let auth: jasmine.SpyObj<AuthService>;
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let savedViewMode: string | null;
  let users: jasmine.SpyObj<UserManagementService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let preferences: Observable<UserPreferences>;

  const user: User = { id: 7, email: 'ada@example.com', firstName: 'Ada', lastName: 'Lovelace' };
  const season: Season = {
    id: 3, name: 'Autumn search', startDate: isoDaysAgo(9), endDate: null, objective: 'Land a Java mission',
    targetDailyRate: 500, active: true
  };

  beforeEach(() => {
    preferences = of({ theme: 'light', language: 'en' } as UserPreferences);
    savedViewMode = localStorage.getItem(DashboardComponent.VIEW_MODE_KEY);
    localStorage.removeItem(DashboardComponent.VIEW_MODE_KEY);
  });

  afterEach(() => {
    if (savedViewMode === null) {
      localStorage.removeItem(DashboardComponent.VIEW_MODE_KEY);
    } else {
      localStorage.setItem(DashboardComponent.VIEW_MODE_KEY, savedViewMode);
    }
  });

  function create(currentUser: User | null = user, statsResult: Observable<DashboardStatsDto> = of(stats()),
                  projectList: Observable<ProjectDto[]> = of([])): void {
    projects = jasmine.createSpyObj('ProjectService', ['getByFreelanceId', 'getDashboardStats']);
    projects.getByFreelanceId.and.returnValue(projectList);
    projects.getDashboardStats.and.returnValue(statsResult);
    auth = jasmine.createSpyObj('AuthService', ['getUser', 'logout']);
    auth.getUser.and.returnValue(currentUser);
    users = jasmine.createSpyObj('UserManagementService', ['getUserPreferences', 'updateUserPreferences']);
    users.getUserPreferences.and.returnValue(preferences);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [DashboardComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ProjectService, useValue: projects },
        { provide: AuthService, useValue: auth },
        { provide: UserManagementService, useValue: users },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    TestBed.overrideComponent(DashboardComponent, {
      remove: { imports: [KanbanBoardComponent, SeasonPickerComponent, DashboardOffersComponent] },
      add: { imports: [KanbanBoardStubComponent, SeasonPickerStubComponent, DashboardOffersStubComponent] }
    });
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(): string {
    return element().textContent!.replace(/\s+/g, ' ');
  }

  function canvas(name: string): HTMLCanvasElement {
    return element().querySelectorAll<HTMLCanvasElement>('canvas')[name === 'status' ? 0 : 1];
  }

  function picker(): SeasonPickerStubComponent {
    return fixture.debugElement.query(debug => debug.componentInstance instanceof SeasonPickerStubComponent).componentInstance;
  }

  describe('layout', () => {
    const arranged: DashboardWidget[] = [{ id: 'RECENT', visible: true }, { id: 'STATS', visible: false },
      { id: 'CHARTS', visible: true }];
    const widgets = (): (string | null)[] =>
      Array.from(element().querySelectorAll('[data-widget]')).map(widget => widget.getAttribute('data-widget'));
    const closing = (result: DashboardWidget[] | undefined) =>
      dialog.open.and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);

    it('shows the blocks in the usual order until the account arranges them', () => {
      create();
      picker().selectionChange.emit(null);
      expect(widgets()).toEqual(['STATS', 'CHARTS', 'REMINDERS', 'OFFERS', 'ANALYTICS', 'RECENT']);
    });

    it('shows the account\'s blocks in its order, without the hidden ones', () => {
      preferences = of({ dashboardWidgets: arranged } as UserPreferences);
      create();
      picker().selectionChange.emit(null);
      expect(widgets()).toEqual(['RECENT', 'CHARTS']);
    });

    it('saves a new layout with the other preferences, and puts the old one back when it cannot', fakeAsync(() => {
      create();
      picker().selectionChange.emit(null);
      closing(arranged);
      users.updateUserPreferences.and.returnValue(of({ theme: 'light', language: 'en', dashboardWidgets: arranged } as UserPreferences));
      component.customize();
      tick();
      expect(users.updateUserPreferences).toHaveBeenCalledWith(jasmine.objectContaining({ theme: 'light', dashboardWidgets: arranged }));
      expect(widgets()).toEqual(['RECENT', 'CHARTS']);
      expect(notifications.success).toHaveBeenCalledWith('dashboard.widgets.saved');

      users.updateUserPreferences.and.returnValue(throwError(() => new Error('offline')));
      closing([{ id: 'OFFERS', visible: true }]);
      component.customize();
      tick();
      expect(widgets()).toEqual(['RECENT', 'CHARTS']);
      expect(notifications.error).toHaveBeenCalledWith('dashboard.widgets.saveError');
    }));

    it('changes nothing when cancelled, or before the preferences are read', () => {
      create();
      closing(undefined);
      component.customize();
      expect(users.updateUserPreferences).not.toHaveBeenCalled();

      TestBed.resetTestingModule();
      preferences = throwError(() => new Error('offline'));
      create();
      closing(arranged);
      component.customize();
      expect(users.updateUserPreferences).not.toHaveBeenCalled();
      expect(component.widgets.length).toBe(6);
    });
  });

  describe('start', () => {
    it('greets the user and waits for the season picker before loading', () => {
      create();

      expect(component.currentUser).toEqual(user);
      expect(component.seasonResolved).toBeFalse();
      expect(text()).toContain(', Ada !');
      expect(picker().freelanceId).toBe(7);
      expect(projects.getDashboardStats).not.toHaveBeenCalled();
    });

    it('stays usable without a signed-in user', () => {
      create(null);

      expect(component.seasonResolved).toBeTrue();
      expect(element().querySelector('app-season-picker')).toBeNull();
      component.loadDashboardData();
      expect(projects.getByFreelanceId).not.toHaveBeenCalled();
      expect(text()).toContain('dashboard.noProjectsYet');
    });

    it('reopens the view the user last looked at', () => {
      localStorage.setItem(DashboardComponent.VIEW_MODE_KEY, 'kanban');
      create();

      expect(component.viewMode).toBe('kanban');
      expect(component.isOverviewMode()).toBeFalse();
    });

    it('opens the overview when storage is unavailable', () => {
      spyOn(Storage.prototype, 'getItem').and.throwError('denied');
      create();

      expect(component.viewMode).toBe('overview');
    });

    it('greets according to the time of day', () => {
      create();
      jasmine.clock().install();
      try {
        jasmine.clock().mockDate(new Date(2026, 9, 26, 9));
        expect(component.getGreeting()).toBe('dashboard.greetingMorning');
        jasmine.clock().mockDate(new Date(2026, 9, 26, 14));
        expect(component.getGreeting()).toBe('dashboard.greetingAfternoon');
        jasmine.clock().mockDate(new Date(2026, 9, 26, 20));
        expect(component.getGreeting()).toBe('dashboard.greetingEvening');
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('signs the user out', () => {
      create();

      component.logout();

      expect(auth.logout).toHaveBeenCalled();
    });
  });

  describe('every season', () => {
    it('loads the statistics of every opportunity and draws the charts', () => {
      create(user, of(stats()), of([1, 9, 4, 2, 8, 3, 7].map(id => project(id))));

      picker().selectionChange.emit(null);

      expect(component.seasonResolved).toBeTrue();
      expect(projects.getByFreelanceId).toHaveBeenCalledWith(7);
      expect(projects.getDashboardStats).toHaveBeenCalledOnceWith(7, null);
      expect(component.globalStats).toBeNull();
      expect(component.stats).toEqual({
        totalProjects: 12, averageDailyRate: 600, totalRevenue: 120000, forecastRevenue: 80000, activeProjects: 4
      });
      expect(component.recentProjects.map(p => p.id)).toEqual([9, 8, 7, 4, 3, 2]);
      expect(text()).toContain('120,000 €');
      expect(element().querySelector('.season-banner')).toBeNull();

      const statusChart = Chart.getChart(canvas('status'))!;
      expect(statusChart.data.labels).toEqual(['projects.statuses.IDEA', 'projects.statuses.WON', 'projects.statuses.UNKNOWN']);
      expect(statusChart.data.datasets[0].data).toEqual([2, 3, 1]);
      expect((statusChart.data.datasets[0].backgroundColor as string[])[2]).toBe('#999');
      expect(Chart.getChart(canvas('workMode'))!.data.labels).toEqual(['projects.workModes.REMOTE', 'projects.workModes.HYBRID']);
    });

    it('hands the statistics to the analytics', () => {
      create();
      component.onSeasonChange(null);
      element();

      const analytics = fixture.debugElement.query(debug => debug.componentInstance instanceof DashboardAnalyticsComponent);
      expect((analytics.componentInstance as DashboardAnalyticsComponent).stats).toBe(component.dashboardStats);
      expect(text()).toContain('dashboard.analyticsTitle');
    });

    it('shows the recent opportunities with their details', () => {
      create(user, of(stats()), of([
        project(1, undefined, { workMode: 'REMOTE', techStack: 'Java, Spring' } as Partial<ProjectDto>), project(2)
      ]));
      component.onSeasonChange(null);

      const cards = element().querySelectorAll('.project-card');
      expect(cards.length).toBe(2);
      expect(cards[0].textContent).toContain('Role 2');
      expect(cards[1].textContent).toContain('projects.workModes.REMOTE');
      expect(cards[1].textContent).toContain('Java, Spring');
      expect(cards[0].querySelector('.project-tech')).toBeNull();
    });

    it('logs loading failures and keeps the page empty', () => {
      const consoleError = spyOn(console, 'error');
      create(user, throwError(() => new Error('offline')), throwError(() => new Error('offline')));

      component.onSeasonChange(null);

      expect(consoleError).toHaveBeenCalledWith('Error loading recent projects:', jasmine.any(Error));
      expect(consoleError).toHaveBeenCalledWith('Error loading dashboard stats:', jasmine.any(Error));
      expect(component.dashboardStats).toBeNull();
      expect(canvas('status')).toBeUndefined();
    });

    it('drops a stale statistics request when the season changes again', () => {
      const first = new Subject<DashboardStatsDto>();
      create(user, first);
      component.onSeasonChange(null);
      projects.getDashboardStats.and.returnValue(of(stats({ totalProjects: 2 })));

      component.onSeasonChange(null);
      first.next(stats({ totalProjects: 99 }));
      first.complete();

      expect(component.stats.totalProjects).toBe(2);
    });
  });

  describe('a season', () => {
    it('compares the season with every season', () => {
      create(user, of(stats()), of([project(1, 3), project(2, 4), project(5, 3)]));
      projects.getDashboardStats.withArgs(7).and.returnValue(of(stats({ totalProjects: 40, wonProjects: 6, lostProjects: 6 })));

      component.onSeasonChange(season);

      expect(projects.getDashboardStats).toHaveBeenCalledWith(7, 3);
      expect(projects.getDashboardStats).toHaveBeenCalledWith(7);
      expect(component.recentProjects.map(p => p.id)).toEqual([5, 1]);
      expect(component.seasonSuccessRate()).toBe(75);
      expect(component.globalSuccessRate()).toBe(50);
      const banner = element().querySelector('.season-banner')!.textContent!.replace(/\s+/g, ' ');
      expect(banner).toContain('Autumn search');
      expect(banner).toContain('seasons.running');
      expect(banner).toContain('seasons.ongoing');
      expect(banner).toContain('Land a Java mission');
      expect(banner).toContain('75 %');
      expect(banner).toContain('600 / 500 €');
      expect(element().querySelector('.metric-compare.on-target')).not.toBeNull();
    });

    it('describes closed and upcoming seasons', () => {
      create();
      component.selectedSeason = { ...season, active: false, endDate: isoDaysAgo(1), objective: null, targetDailyRate: null };
      expect(element().querySelector('.season-state')!.textContent).toContain('seasons.closed');
      expect(element().querySelector('.season-objective')).toBeNull();

      component.selectedSeason = { ...season, active: false, startDate: isoDaysAgo(-5) };
      expect(element().querySelector('.season-state')!.textContent).toContain('seasons.upcoming');
      expect(text()).toContain('—');
    });

    it('counts the days of a season up to today or its end', () => {
      create();

      expect(component.seasonDays({ ...season, startDate: isoDaysAgo(9) })).toBe(10);
      expect(component.seasonDays({ ...season, startDate: isoDaysAgo(30), endDate: isoDaysAgo(21) })).toBe(10);
      expect(component.seasonDays({ ...season, startDate: isoDaysAgo(2), endDate: isoDaysAgo(-30) })).toBe(3);
      expect(component.seasonDays({ ...season, startDate: isoDaysAgo(-5) })).toBe(1);
    });

    it('measures the average rate against the target of the season', () => {
      create();

      expect(component.targetProgress({ ...season, targetDailyRate: null })).toBeNull();
      expect(component.targetProgress(season)).toBeNull();
      component.stats.averageDailyRate = 450;
      expect(component.targetProgress(season)).toBe(90);
    });
  });

  describe('success rate', () => {
    it('is unknown before any decision', () => {
      expect(DashboardComponent.successRate(null)).toBeNull();
      expect(DashboardComponent.successRate(stats({ wonProjects: 0, lostProjects: 0 }))).toBeNull();
      expect(DashboardComponent.successRate(stats({ wonProjects: 1, lostProjects: 2 }))).toBe(33);
    });
  });

  describe('view switch', () => {
    it('remembers the pipeline board and shows it for the selected season', () => {
      create();
      component.onSeasonChange(season);

      component.switchView('kanban');

      expect(localStorage.getItem(DashboardComponent.VIEW_MODE_KEY)).toBe('kanban');
      expect(element().querySelector('.overview-content')).toBeNull();
      const board = fixture.debugElement.query(debug => debug.componentInstance instanceof KanbanBoardStubComponent);
      expect((board.componentInstance as KanbanBoardStubComponent).seasonId).toBe(3);
    });

    it('redraws the charts when coming back to the overview', fakeAsync(() => {
      create();
      component.onSeasonChange(null);
      component.switchView('kanban');
      expect(canvas('status')).toBeUndefined();

      component.switchView('overview');
      tick();

      expect(Chart.getChart(canvas('status'))).toBeDefined();
      expect(Chart.getChart(canvas('workMode'))).toBeDefined();
    }));

    it('ignores unknown views', () => {
      create();

      component.switchView('calendar');

      expect(component.viewMode).toBe('overview');
      expect(localStorage.getItem(DashboardComponent.VIEW_MODE_KEY)).toBeNull();
    });

    it('still switches when storage is unavailable', () => {
      create();
      spyOn(Storage.prototype, 'setItem').and.throwError('denied');

      component.switchView('kanban');

      expect(component.viewMode).toBe('kanban');
    });

    it('switches from the toggle buttons', () => {
      create();

      const toggles = element().querySelectorAll<HTMLButtonElement>('mat-button-toggle button');
      toggles[1].click();

      expect(component.viewMode).toBe('kanban');
    });
  });

  it('destroys its charts when leaving the page', () => {
    create();
    component.onSeasonChange(null);
    const status = canvas('status');
    expect(Chart.getChart(status)).toBeDefined();

    fixture.destroy();

    expect(Chart.getChart(status)).toBeUndefined();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { FreelanceDto, PROJECT_STATUS_COLORS, ProjectDto, ProjectStatus } from '../../../models';
import { Season } from '../../../models/season.models';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { FreelanceService } from '../../../services/freelance/freelance.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { SeasonService } from '../../../services/season/season.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { KeyboardShortcutsService } from '../../../shared/shortcuts/keyboard-shortcuts.service';
import { ProjectListComponent } from './project-list.component';

/** An ISO date (yyyy-MM-dd) the given number of months away from today. */
function monthsFromNow(months: number): string {
  const date = new Date();
  date.setMonth(date.getMonth() + months);
  return date.toISOString().slice(0, 10);
}

describe('ProjectListComponent', () => {
  const missions: ProjectDto[] = [
    { id: 1, role: 'Développeur Angular', clientName: 'Airbus', dailyRate: 600, workMode: 'REMOTE', startDate: '2026-01-05',
      durationInMonths: 6, techStack: 'Angular, TypeScript', status: ProjectStatus.CONTACT, description: 'Refonte du portail' },
    { id: 2, role: 'Architecte Java', clientName: 'Société Générale', dailyRate: 750, workMode: 'ONSITE', startDate: '2026-03-01',
      durationInMonths: 12, techStack: 'Java, Spring', status: ProjectStatus.WON },
    { id: 3, role: 'Lead Python', clientName: 'Thales', dailyRate: 500, workMode: 'HYBRID', startDate: '2025-06-01',
      techStack: 'Python, Docker' }
  ] as ProjectDto[];

  let projectService: jasmine.SpyObj<ProjectService>;
  let freelanceService: jasmine.SpyObj<FreelanceService>;
  let seasonService: jasmine.SpyObj<SeasonService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let shortcuts: { focusProjectSearch$: Subject<boolean>; acknowledgeSearchFocus: jasmine.Spy };

  function create(projects: ProjectDto[] = missions, user: { id: number } | null = { id: 7 },
                  seasons: Season[] = [], before: () => void = () => undefined): ComponentFixture<ProjectListComponent> {
    projectService = jasmine.createSpyObj('ProjectService', ['getByFreelanceId', 'delete', 'downloadYearlySummary']);
    projectService.getByFreelanceId.and.returnValue(of(projects.map(project => ({ ...project }))));
    freelanceService = jasmine.createSpyObj('FreelanceService', ['getById', 'update']);
    freelanceService.getById.and.returnValue(of({ id: 7 } as FreelanceDto));
    seasonService = jasmine.createSpyObj('SeasonService', ['getByFreelanceId']);
    seasonService.getByFreelanceId.and.returnValue(of(seasons));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmDialog.confirm.and.returnValue(of(true));
    shortcuts = { focusProjectSearch$: new Subject<boolean>(), acknowledgeSearchFocus: jasmine.createSpy('acknowledgeSearchFocus') };
    TestBed.configureTestingModule({
      imports: [ProjectListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: ProjectService, useValue: projectService },
        { provide: FreelanceService, useValue: freelanceService },
        { provide: SeasonService, useValue: seasonService },
        { provide: CommuteService, useValue: jasmine.createSpyObj('CommuteService', ['getProjectsSortedByCommute']) },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog },
        { provide: KeyboardShortcutsService, useValue: shortcuts }
      ]
    });
    before();
    const fixture = TestBed.createComponent(ProjectListComponent);
    fixture.componentInstance.filtersExpanded = true;
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProjectListComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function titles(fixture: ComponentFixture<ProjectListComponent>): string[] {
    fixture.detectChanges();
    return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.project-title')).map(el => el.textContent!.trim());
  }

  function many(count: number): ProjectDto[] {
    return Array.from({ length: count }, (_, i) => ({
      id: i + 1, role: `Mission ${i + 1}`, dailyRate: 500, startDate: `2026-01-${String(i + 1).padStart(2, '0')}`
    }) as ProjectDto);
  }

  it('lists the freelance\'s projects, latest start first, with their net revenue', () => {
    const fixture = create();

    expect(projectService.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(seasonService.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(freelanceService.getById).toHaveBeenCalledWith(7);
    expect(titles(fixture)).toEqual(['Architecte Java', 'Développeur Angular', 'Lead Python']);
    const page = text(fixture);
    expect(page).toContain('projects.resultsCount');
    expect(page).toContain('Airbus');
    expect(page).toContain('projects.workModes.REMOTE');
    expect(page).toContain('Angular, TypeScript');
    expect(page).toContain('Refonte du portail');
    // 600 €/day at the default 45 % reversion rate: 330 €/day, 5,995 €/month and 71,940 €/year over 218 days.
    expect(page).toContain('330 €');
    expect(page).toContain('5,995 €');
    expect(page).toContain('71,940 €');
    expect(fixture.componentInstance.isLoading).toBeFalse();
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
  });

  it('shows each project\'s pipeline stage, a project without one as a first contact', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(text(fixture)).toContain('projects.statuses.CONTACT');
    expect(text(fixture)).toContain('projects.statuses.WON');
    expect(component.getStatusColor(missions[2])).toBe(PROJECT_STATUS_COLORS[ProjectStatus.CONTACT]);
    expect(component.getStatusColor(missions[1])).toBe(PROJECT_STATUS_COLORS[ProjectStatus.WON]);
    expect(component.getWorkModeLabel('HYBRID')).toBe('projects.workModes.HYBRID');
  });

  it('tells where a signed mission stands, and nothing for an opportunity', () => {
    const fixture = create([]);
    const component = fixture.componentInstance;
    const won = (startDate?: string, durationInMonths?: number): ProjectDto =>
      ({ id: 9, role: 'Mission', dailyRate: 500, status: ProjectStatus.WON, startDate, durationInMonths }) as ProjectDto;

    expect(component.getMissionPhaseKey(won(monthsFromNow(2), 6))).toBe('projects.upcoming');
    expect(component.getMissionPhaseKey(won(monthsFromNow(-2), 6))).toBe('projects.inProgress');
    expect(component.getMissionPhaseKey(won(monthsFromNow(-12), 6))).toBe('projects.completed');
    expect(component.getMissionPhaseKey(won(monthsFromNow(-2)))).toBeNull();
    expect(component.getMissionPhaseKey(won())).toBeNull();
    expect(component.getMissionPhaseKey({ ...won(monthsFromNow(-2), 6), status: ProjectStatus.OFFER })).toBeNull();
  });

  it('shows the delivery phase next to the start date of a signed mission', () => {
    const fixture = create([{ id: 1, role: 'Mission en cours', dailyRate: 500, status: ProjectStatus.WON,
      startDate: monthsFromNow(-1), durationInMonths: 6 } as ProjectDto]);

    expect(fixture.nativeElement.querySelector('.mission-phase').textContent).toContain('projects.inProgress');
  });

  it('invites to add a first project when there is none', () => {
    const fixture = create([]);

    const page = text(fixture);
    expect(page).toContain('projects.noProjects');
    expect(page).toContain('projects.startFirstProject');
    expect(page).toContain('projects.addProject');
    expect(page).not.toContain('projects.reversion.title');
  });

  it('offers to clear filters that match no project', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.filterForm.patchValue({ searchQuery: 'Cobol' });

    const page = text(fixture);
    expect(page).toContain('projects.noMatchingProjects');
    expect(page).toContain('projects.tryDifferentFilters');
    const clear = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.empty-state button'))
      .find(button => button.textContent!.includes('common.clearAllFilters'))!;
    clear.click();

    expect(component.filterForm.value.searchQuery).toBeNull();
    expect(titles(fixture).length).toBe(3);
  });

  it('reports projects that could not be loaded', () => {
    const consoleError = spyOn(console, 'error');
    const fixture = create(missions, { id: 7 }, [],
      () => projectService.getByFreelanceId.and.returnValue(throwError(() => new Error('down'))));

    expect(notifications.error).toHaveBeenCalledWith('projects.loadError');
    expect(fixture.componentInstance.isLoading).toBeFalse();
    expect(consoleError).toHaveBeenCalled();
    expect(text(fixture)).not.toContain('projects.loading');
  });

  it('loads nothing without a signed-in user', () => {
    const fixture = create(missions, null);
    const component = fixture.componentInstance;

    component.exportSummary();
    component.loadCommuteData();
    component.saveReversionRate();

    expect(projectService.getByFreelanceId).not.toHaveBeenCalled();
    expect(seasonService.getByFreelanceId).not.toHaveBeenCalled();
    expect(freelanceService.getById).not.toHaveBeenCalled();
    expect(projectService.downloadYearlySummary).not.toHaveBeenCalled();
    expect(freelanceService.update).not.toHaveBeenCalled();
    expect(text(fixture)).toContain('projects.noProjects');
  });

  describe('seasons', () => {
    it('offers to filter by season once the freelance has some', () => {
      const fixture = create(missions, { id: 7 }, [{ id: 4, name: 'Automne', startDate: '2026-09-01', active: true }]);

      expect(fixture.componentInstance.seasons.length).toBe(1);
      expect(fixture.nativeElement.querySelector('mat-select[formControlName="season"]')).not.toBeNull();
    });

    it('hides the season filter when seasons cannot be loaded', () => {
      const fixture = create(missions, { id: 7 }, [],
        () => seasonService.getByFreelanceId.and.returnValue(throwError(() => new Error('down'))));

      expect(fixture.componentInstance.seasons).toEqual([]);
      expect(fixture.nativeElement.querySelector('mat-select[formControlName="season"]')).toBeNull();
      expect(titles(fixture).length).toBe(3);
    });
  });

  describe('deleting a project', () => {
    it('removes it from the list once confirmed', () => {
      const fixture = create();
      projectService.delete.and.returnValue(of(undefined));

      const deleteButton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.project-actions button'))
        .find(button => button.textContent!.includes('common.delete'))!;
      deleteButton.click();

      expect(confirmDialog.confirm).toHaveBeenCalledWith({ messageKey: 'projects.deleteConfirm', danger: true });
      expect(projectService.delete).toHaveBeenCalledWith(2);
      expect(titles(fixture)).toEqual(['Développeur Angular', 'Lead Python']);
      expect(notifications.success).toHaveBeenCalledWith('projects.deleteSuccess');
    });

    it('keeps the page shown, or the last one left', () => {
      const fixture = create(many(25));
      const component = fixture.componentInstance;
      projectService.delete.and.returnValue(of(undefined));
      component.onPageChange({ pageIndex: 1, pageSize: 12, length: 25 });

      component.deleteProject(1);
      expect(component.pageIndex).toBe(1);
      expect(component.pagedProjects.length).toBe(12);

      component.onPageChange({ pageIndex: 2, pageSize: 12, length: 24 });
      expect(component.pageIndex).toBe(1);
      component.deleteProject(2);
      expect(component.pageIndex).toBe(1);
      expect(component.pagedProjects.length).toBe(11);
    });

    it('keeps it when not confirmed', () => {
      const fixture = create();
      confirmDialog.confirm.and.returnValue(of(false));

      fixture.componentInstance.deleteProject(1);

      expect(projectService.delete).not.toHaveBeenCalled();
      expect(titles(fixture).length).toBe(3);
    });

    it('reports a failed deletion', () => {
      const consoleError = spyOn(console, 'error');
      const fixture = create();
      projectService.delete.and.returnValue(throwError(() => new Error('down')));

      fixture.componentInstance.deleteProject(1);

      expect(notifications.error).toHaveBeenCalledWith('projects.deleteError');
      expect(notifications.success).not.toHaveBeenCalled();
      expect(titles(fixture).length).toBe(3);
      expect(consoleError).toHaveBeenCalled();
    });
  });

  describe('yearly summary', () => {
    it('downloads this year\'s summary as a CSV file', () => {
      const fixture = create();
      const year = new Date().getFullYear();
      const blob = new Blob(['role;rate'], { type: 'text/csv' });
      projectService.downloadYearlySummary.and.returnValue(of(blob));
      const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:summary');
      spyOn(URL, 'revokeObjectURL');
      let downloaded = '';
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        downloaded = this.download;
      });

      const exportButton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.header-actions button'))
        .find(button => button.textContent!.includes('projects.exportSummary'))!;
      exportButton.click();

      expect(projectService.downloadYearlySummary).toHaveBeenCalledWith(7, year);
      expect(createUrl).toHaveBeenCalledWith(blob);
      expect(downloaded).toBe(`indezy-summary-${year}.csv`);
    });

    it('reports a summary that could not be exported', () => {
      const fixture = create();
      projectService.downloadYearlySummary.and.returnValue(throwError(() => new Error('down')));

      fixture.componentInstance.exportSummary();

      expect(notifications.error).toHaveBeenCalledWith('projects.exportError');
    });
  });

  describe('pagination', () => {
    it('shows twelve projects a page and pages through the rest', () => {
      const fixture = create(many(30));
      const component = fixture.componentInstance;

      expect(titles(fixture).length).toBe(12);
      expect(fixture.nativeElement.querySelector('mat-paginator')).not.toBeNull();

      component.onPageChange({ pageIndex: 2, pageSize: 12, length: 30 });
      expect(titles(fixture).length).toBe(6);
      expect(component.pagedProjects[0].role).toBe('Mission 6');
    });

    it('stays within the pages left when the page size grows', () => {
      const fixture = create(many(30));
      const component = fixture.componentInstance;

      component.onPageChange({ pageIndex: 5, pageSize: 24, length: 30 });

      expect(component.pageIndex).toBe(1);
      expect(component.pagedProjects.length).toBe(6);
    });

    it('goes back to the first page when the filters change', () => {
      const fixture = create(many(30));
      const component = fixture.componentInstance;
      component.onPageChange({ pageIndex: 2, pageSize: 12, length: 30 });

      component.filterForm.patchValue({ searchQuery: 'Mission' });

      expect(component.pageIndex).toBe(0);
    });
  });

  it('opens the filters and focuses the search on the keyboard shortcut', async () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.filtersExpanded = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formControlName="searchQuery"]')).toBeNull();

    shortcuts.focusProjectSearch$.next(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.filtersExpanded).toBeTrue();
    expect(shortcuts.acknowledgeSearchFocus).toHaveBeenCalled();
    const search = fixture.nativeElement.querySelector('input[formControlName="searchQuery"]');
    expect(document.activeElement).toBe(search);
  });
});

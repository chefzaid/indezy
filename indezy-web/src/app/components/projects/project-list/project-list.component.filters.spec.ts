import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, of, throwError } from 'rxjs';
import { FreelanceDto, ProjectDto, ProjectStatus } from '../../../models';
import { CommuteInfoDto, ProjectCommuteDto } from '../../../models/commute.models';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { FreelanceService } from '../../../services/freelance/freelance.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { SeasonService } from '../../../services/season/season.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { KeyboardShortcutsService } from '../../../shared/shortcuts/keyboard-shortcuts.service';
import { ProjectListComponent } from './project-list.component';

describe('ProjectListComponent filters, commute and reversion rate', () => {
  const missions: ProjectDto[] = [
    { id: 1, role: 'Développeur Angular', clientName: 'Airbus', dailyRate: 600, workMode: 'REMOTE', startDate: '2026-01-05',
      durationInMonths: 6, techStack: 'Angular, TypeScript', status: ProjectStatus.CONTACT },
    { id: 2, role: 'Architecte Java', clientName: 'Société Générale', dailyRate: 750, workMode: 'ONSITE', startDate: '2026-03-01',
      durationInMonths: 12, techStack: 'Java, Spring', status: ProjectStatus.WON },
    { id: 3, role: 'Lead Python', clientName: 'Thales', dailyRate: 500, workMode: 'HYBRID', startDate: '2025-06-01',
      techStack: 'Python, Docker', daysPerYear: 200 }
  ] as ProjectDto[];
  const profile = { id: 7, firstName: 'Zaid', reversionRate: 70, incomeTaxRate: 10 } as FreelanceDto;

  let freelanceService: jasmine.SpyObj<FreelanceService>;
  let commuteService: jasmine.SpyObj<CommuteService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(freelance: FreelanceDto = profile,
                  before: () => void = () => undefined): ComponentFixture<ProjectListComponent> {
    const projectService = jasmine.createSpyObj('ProjectService', ['getByFreelanceId']);
    projectService.getByFreelanceId.and.returnValue(of(missions.map(project => ({ ...project }))));
    freelanceService = jasmine.createSpyObj('FreelanceService', ['getById', 'update']);
    freelanceService.getById.and.returnValue(of({ ...freelance }));
    commuteService = jasmine.createSpyObj('CommuteService', ['getProjectsSortedByCommute']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProjectListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: ProjectService, useValue: projectService },
        { provide: FreelanceService, useValue: freelanceService },
        { provide: SeasonService, useValue: { getByFreelanceId: () => of([]) } },
        { provide: CommuteService, useValue: commuteService },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: { confirm: () => of(false) } },
        { provide: KeyboardShortcutsService, useValue: { focusProjectSearch$: EMPTY, acknowledgeSearchFocus: () => undefined } }
      ]
    });
    before();
    const fixture = TestBed.createComponent(ProjectListComponent);
    fixture.componentInstance.filtersExpanded = true;
    fixture.detectChanges();
    return fixture;
  }

  function titles(fixture: ComponentFixture<ProjectListComponent>): string[] {
    fixture.detectChanges();
    return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.project-title')).map(el => el.textContent!.trim());
  }

  function text(fixture: ComponentFixture<ProjectListComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function commute(projectId: number, seconds?: number, overrides: Partial<CommuteInfoDto> = {}): CommuteInfoDto {
    return { projectId, projectRole: 'Mission', origin: 'Paris', destination: 'Lyon', travelMode: 'DRIVING',
      durationInSeconds: seconds, durationText: seconds ? `${seconds / 60} min` : undefined,
      distanceText: seconds ? `${seconds / 60} km` : undefined, remote: false, onsiteDaysPerMonth: 20,
      monthlySeconds: seconds ? seconds * 40 : null, ...overrides };
  }

  describe('filters', () => {
    it('narrows the list to what is searched and counts the active filters', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      const search: HTMLInputElement = fixture.nativeElement.querySelector('input[formControlName="searchQuery"]');

      search.value = 'java';
      search.dispatchEvent(new Event('input'));

      expect(titles(fixture)).toEqual(['Architecte Java']);
      expect(component.getFilterCount()).toBe(1);
      expect(fixture.nativeElement.querySelector('.filter-count').textContent).toContain('(1)');

      component.filterForm.patchValue({ searchQuery: '', minRate: 550, workMode: 'REMOTE' });
      expect(titles(fixture)).toEqual(['Développeur Angular']);
      expect(component.getFilterCount()).toBe(2);
    });

    it('sorts by the chosen field and order', () => {
      const fixture = create();
      const component = fixture.componentInstance;

      component.filterForm.patchValue({ sortBy: 'dailyRate', sortOrder: 'asc' });
      expect(titles(fixture)).toEqual(['Lead Python', 'Développeur Angular', 'Architecte Java']);

      component.filterForm.patchValue({ sortBy: 'clientName', sortOrder: 'desc' });
      expect(titles(fixture)).toEqual(['Lead Python', 'Architecte Java', 'Développeur Angular']);

      component.filterForm.patchValue({ sortBy: '' });
      expect(titles(fixture).length).toBe(3);
    });

    it('clears every filter, back to the latest start first', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      component.filterForm.patchValue({ searchQuery: 'python', sortBy: 'role', sortOrder: 'asc', selectedTechStack: ['Python'] });
      expect(titles(fixture)).toEqual(['Lead Python']);

      const clear: HTMLButtonElement = fixture.nativeElement.querySelector('.clear-filters-btn');
      clear.click();

      expect(component.filterForm.value).toEqual(jasmine.objectContaining({
        searchQuery: null, sortBy: 'startDate', sortOrder: 'desc', selectedTechStack: []
      }));
      expect(component.getFilterCount()).toBe(0);
      expect(titles(fixture)).toEqual(['Architecte Java', 'Développeur Angular', 'Lead Python']);
      expect(fixture.nativeElement.querySelector('.clear-filters-btn')).toBeNull();
    });

    it('folds and unfolds the filters', () => {
      const fixture = create();
      const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('.filters-toggle');

      toggle.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.filtersExpanded).toBeFalse();
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(fixture.nativeElement.querySelector('.filters-form')).toBeNull();

      toggle.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.filters-form')).not.toBeNull();
    });

    it('adds each technology once and removes it', () => {
      const fixture = create();
      const component = fixture.componentInstance;

      component.addTechStack('Docker');
      component.addTechStack('Angular');
      component.addTechStack('Docker');
      expect(component.filterForm.value.selectedTechStack).toEqual(['Docker', 'Angular']);
      expect(titles(fixture)).toEqual(['Développeur Angular', 'Lead Python']);
      expect(text(fixture)).toContain('Docker');

      component.removeTechStack('Docker');
      component.removeTechStack('React');
      expect(component.filterForm.value.selectedTechStack).toEqual(['Angular']);
      expect(titles(fixture)).toEqual(['Développeur Angular']);
    });

    it('treats a missing technology selection as none', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      component.filterForm.get('selectedTechStack')!.setValue(null);

      component.removeTechStack('Java');
      expect(component.filterForm.value.selectedTechStack).toBeNull();
      component.addTechStack('Java');
      expect(component.filterForm.value.selectedTechStack).toEqual(['Java']);
    });
  });

  describe('commute', () => {
    const results: ProjectCommuteDto[] = [
      { project: missions[0], commute: commute(1, 3600) },
      { project: missions[1], commute: commute(2, 1200) },
      { project: missions[2] },
      { project: { role: 'Sans id', dailyRate: 400 } as ProjectDto, commute: commute(0, 60) }
    ];

    it('sorts by commute time once the times are loaded', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      commuteService.getProjectsSortedByCommute.and.returnValue(of(results));

      component.filterForm.patchValue({ sortBy: 'commuteTime', sortOrder: 'desc' });
      component.onSortByChange('commuteTime');
      expect(component.filterForm.value.sortOrder).toBe('asc');

      expect(commuteService.getProjectsSortedByCommute).toHaveBeenCalledWith(7, 'DRIVING');
      expect(component.isCommuteSortActive).toBeTrue();
      expect(component.isCommuteLoading).toBeFalse();
      expect(component.commuteData.size).toBe(2);
      expect(titles(fixture)).toEqual(['Architecte Java', 'Développeur Angular', 'Lead Python']);
      const page = text(fixture);
      expect(page).toContain('20 min');
      expect(page).toContain('(20 km)');
      expect(page).toContain('projects.commute.travelMode');
      expect(component.getCommuteInfo(1)?.durationInSeconds).toBe(3600);
      expect(component.getCommuteInfo(undefined)).toBeUndefined();
      expect(component.getCommuteInfo(3)).toBeUndefined();

      component.onSortByChange('commuteTime');
      component.onSortByChange('dailyRate');
      expect(commuteService.getProjectsSortedByCommute).toHaveBeenCalledTimes(1);
    });

    it('says why the commute of a project is unknown', () => {
      const fixture = create();
      commuteService.getProjectsSortedByCommute.and.returnValue(of([{ project: missions[0], commute: commute(1) },
        { project: missions[1], commute: commute(2, undefined, { unavailable: 'NO_HOME_ADDRESS' }) }]));

      fixture.componentInstance.loadCommuteData();

      expect(text(fixture)).toContain('projects.commute.reasons.LOOKUP_FAILED');
      expect(text(fixture)).toContain('projects.commute.reasons.NO_HOME_ADDRESS');
      expect(fixture.nativeElement.querySelector('[data-commute="2"] a[href="/profile"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-commute="1"] a')).toBeNull();
    });

    it('shows a trip estimated without Google as such', () => {
      const fixture = create();
      commuteService.getProjectsSortedByCommute.and.returnValue(of([
        { project: missions[0], commute: commute(1, undefined, { durationInSeconds: 2820, rushDurationInSeconds: 6300, distanceInMeters: 9700,
          estimated: true, monthlySeconds: 252000 }) }]));

      fixture.componentInstance.loadCommuteData();
      fixture.detectChanges();

      const shown = fixture.nativeElement.querySelector('[data-commute="1"]').textContent.replace(/\s+/g, ' ');
      expect(shown).toContain('≈ 47 min');
      expect(shown).toContain('(≈ 10 km)');
      expect(fixture.nativeElement.querySelector('[data-commute-rush]')).not.toBeNull();
      expect(shown).toContain('projects.commute.estimate');
      expect(shown).not.toContain('projects.commute.reasons');
      expect(fixture.componentInstance.tripTime(commute(1, undefined, { durationInSeconds: 3900 }))).toBe('1 h 05');
    });

    it('gives a commute\'s hours a month, and none for a remote mission', () => {
      const fixture = create();
      commuteService.getProjectsSortedByCommute.and.returnValue(of([
        { project: missions[0], commute: commute(1, 1800, { onsiteDaysPerMonth: 8, monthlySeconds: 28800 }) },
        { project: missions[1], commute: commute(2, undefined, { remote: true, onsiteDaysPerMonth: 0, monthlySeconds: 0 }) }]));

      fixture.componentInstance.loadCommuteData();

      expect(text(fixture)).toContain('· projects.commute.perMonth');
      expect(fixture.componentInstance.monthlyHours(commute(1, 1800, { monthlySeconds: 28800 }))).toBe(8);
      expect(fixture.componentInstance.monthlyHours(commute(1, 1800, { monthlySeconds: 27000 }))).toBe(7.5);
      expect(fixture.componentInstance.monthlyHours(commute(1, 1800, { monthlySeconds: null }))).toBe(0);
      expect(fixture.nativeElement.querySelector('[data-commute="2"]').textContent).toContain('projects.commute.remote');
      expect(fixture.nativeElement.querySelector('[data-commute="2"] .commute-month')).toBeNull();
    });

    it('reloads the times for another travel mode while sorting by commute', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      commuteService.getProjectsSortedByCommute.and.returnValue(of(results));

      component.onTravelModeChange('TRANSIT');
      expect(component.commuteTravelMode).toBe('TRANSIT');
      expect(commuteService.getProjectsSortedByCommute).not.toHaveBeenCalled();

      component.filterForm.patchValue({ sortBy: 'commuteTime' });
      component.onTravelModeChange('TRANSIT');
      expect(commuteService.getProjectsSortedByCommute).toHaveBeenCalledWith(7, 'TRANSIT');

      component.filterForm.patchValue({ sortBy: 'role' });
      component.onTravelModeChange('DRIVING');
      expect(commuteService.getProjectsSortedByCommute).toHaveBeenCalledWith(7, 'DRIVING');
    });

    it('shows the icon of the travel mode', () => {
      const component = create().componentInstance;

      expect(component.getCommuteIcon('DRIVING')).toBe('directions_car');
      expect(component.getCommuteIcon('TRANSIT')).toBe('directions_transit');
    });

    it('reports commute times that could not be loaded', () => {
      const consoleError = spyOn(console, 'error');
      const fixture = create();
      commuteService.getProjectsSortedByCommute.and.returnValue(throwError(() => new Error('down')));

      fixture.componentInstance.onSortByChange('commuteTime');

      expect(notifications.error).toHaveBeenCalledWith('projects.commuteLoadError');
      expect(fixture.componentInstance.isCommuteLoading).toBeFalse();
      expect(fixture.componentInstance.isCommuteSortActive).toBeFalse();
      expect(consoleError).toHaveBeenCalled();
    });
  });

  describe('reversion rate', () => {
    it('uses the rates saved on the freelance profile', () => {
      const fixture = create();
      const component = fixture.componentInstance;

      expect(component.reversionRate).toBe(70);
      expect(component.incomeTaxRate).toBe(10);
      expect(component.useCustomRate).toBeTrue();
      expect(component.getNetDailyRate(missions[0])).toBe(420);
      expect(component.getNetMonthly(missions[0])).toBe(Math.round(420 * 218 / 12));
      expect(component.getNetYearly(missions[2])).toBe(350 * 200);
      expect(text(fixture)).toContain('projects.reversion.formula');
    });

    it('keeps the default rates when the profile has none', () => {
      const component = create({ id: 7, reversionRate: undefined, incomeTaxRate: undefined } as FreelanceDto).componentInstance;

      expect(component.reversionRate).toBe(55);
      expect(component.incomeTaxRate).toBe(5);
      expect(component.useCustomRate).toBeFalse();
    });

    it('keeps the default rates when the profile cannot be loaded', () => {
      const consoleError = spyOn(console, 'error');
      const component = create(profile,
        () => freelanceService.getById.and.returnValue(throwError(() => new Error('down')))).componentInstance;

      expect(component.freelanceProfile).toBeNull();
      expect(component.reversionRate).toBe(55);
      expect(consoleError).toHaveBeenCalled();
    });

    it('has no net revenue without a daily rate', () => {
      const component = create().componentInstance;
      const unrated = { id: 9, role: 'Sans TJM' } as ProjectDto;

      expect(component.getNetDailyRate(unrated)).toBe(0);
      expect(component.getNetMonthly(unrated)).toBe(0);
      expect(component.getNetYearly(unrated)).toBe(0);
    });

    it('takes a rate typed in, rejecting one out of range', () => {
      const fixture = create({ id: 7 } as FreelanceDto);
      const component = fixture.componentInstance;
      const rateInput: HTMLInputElement = fixture.nativeElement.querySelector('.reversion-field input');

      rateInput.value = '38';
      rateInput.dispatchEvent(new Event('change'));
      expect(component.reversionRate).toBe(38);
      expect(component.useCustomRate).toBeTrue();

      component.onReversionRateChange('32,5');
      expect(component.reversionRate).toBe(32.5);
      component.onReversionRateChange(150);
      component.onReversionRateChange(-1);
      component.onReversionRateChange('abc');
      component.onReversionRateChange(Number.NaN);
      expect(component.reversionRate).toBe(32.5);
    });

    it('keeps the share of the gross the rate says: a lower rate leaves less', () => {
      const component = create().componentInstance;

      component.onReversionRateChange(50);
      expect(component.getNetDailyRate(missions[0])).toBe(300);
      component.onReversionRateChange(40);
      expect(component.getNetDailyRate(missions[0])).toBe(240);
    });

    it('derives the reversion rate from the income tax typed in, a higher tax lowering it', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      const taxInput: HTMLInputElement = fixture.nativeElement.querySelectorAll('.reversion-field input')[1];

      taxInput.value = '12';
      taxInput.dispatchEvent(new Event('change'));
      expect(component.incomeTaxRate).toBe(12);
      expect(component.reversionRate).toBe(48);

      component.onIncomeTaxRateChange('7.5');
      expect(component.reversionRate).toBe(52.5);
      component.onIncomeTaxRateChange(60);
      expect(component.incomeTaxRate).toBe(7.5);
    });

    it('saves the rates on the freelance profile', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      const saved = { ...profile, reversionRate: 35, incomeTaxRate: 10 };
      freelanceService.update.and.returnValue(of(saved));
      component.onReversionRateChange(35);

      const saveButton: HTMLButtonElement = fixture.nativeElement.querySelector('.reversion-controls button');
      saveButton.click();

      expect(freelanceService.update).toHaveBeenCalledWith(7, jasmine.objectContaining({
        id: 7, firstName: 'Zaid', reversionRate: 35, incomeTaxRate: 10
      }));
      expect(component.freelanceProfile).toEqual(saved);
      expect(notifications.success).toHaveBeenCalledWith('projects.reversion.saved', 2000);
    });

    it('reports rates that could not be saved', () => {
      const fixture = create();
      freelanceService.update.and.returnValue(throwError(() => new Error('down')));

      fixture.componentInstance.saveReversionRate();

      expect(notifications.error).toHaveBeenCalledWith('projects.reversion.saveError');
    });

    it('saves nothing before the profile is loaded', () => {
      const component = create(profile, () => freelanceService.getById.and.returnValue(EMPTY)).componentInstance;

      component.saveReversionRate();

      expect(freelanceService.update).not.toHaveBeenCalled();
    });
  });
});

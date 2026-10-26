import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, EMPTY, of, throwError } from 'rxjs';
import { PROJECT_STATUS_COLORS, ProjectDto, ProjectStatus } from '../../../models';
import { UserProfile } from '../../../models/user-management.models';
import { ActivityReportService } from '../../../services/activity-report/activity-report.service';
import { AuthService } from '../../../services/auth/auth.service';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { KanbanLostReasonDialogComponent } from '../../kanban-lost-reason-dialog/kanban-lost-reason-dialog.component';
import { RenameTagDialogComponent } from '../rename-tag-dialog/rename-tag-dialog.component';
import { ProjectDetailComponent } from './project-detail.component';

describe('ProjectDetailComponent', () => {
  const mission: ProjectDto = {
    id: 12, role: 'Développeur Java', status: ProjectStatus.INTERVIEW, dailyRate: 650, clientDailyRate: 800, margin: 150,
    marginPercentage: 18.75, askedDailyRate: 700, offeredDailyRate: 620, workMode: 'HYBRID', remoteDaysPerMonth: 8,
    onsiteDaysPerMonth: 12, startDate: '2026-11-02', durationInMonths: 6, orderRenewalInMonths: 3, daysPerYear: 216,
    techStack: 'Java, Spring, Kafka', description: 'Plateforme de paiement', advantages: 'Tickets restaurant',
    clientId: 3, clientName: 'Société Générale', middlemanName: 'Capgemini', sourceName: 'Free-Work', seasonName: 'Automne',
    personalRating: 4, documents: ['contrat.pdf'], link: 'https://www.free-work.com/mission/12', notes: 'Rappeler jeudi',
    totalSteps: 3, completedSteps: 1
  } as ProjectDto;

  let projectService: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let userManagement: jasmine.SpyObj<UserManagementService>;
  let params: BehaviorSubject<Record<string, string>>;
  let navigate: jasmine.Spy;
  let user: { id: number } | null;

  function create(project: ProjectDto | null = mission, query: Record<string, string> = {},
                  before: () => void = () => undefined): ComponentFixture<ProjectDetailComponent> {
    projectService = jasmine.createSpyObj('ProjectService',
      ['getById', 'getProjectNotes', 'updateStatus', 'renameTag', 'addProjectNote', 'deleteProjectNote', 'delete']);
    projectService.getById.and.returnValue(of(project as ProjectDto));
    projectService.getProjectNotes.and.returnValue(of([]));
    const steps = jasmine.createSpyObj('InterviewStepService', ['getByProjectIdOrderByDate', 'updateStatus', 'delete']);
    steps.getByProjectIdOrderByDate.and.returnValue(of([]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmDialog.confirm.and.returnValue(of(true));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    userManagement = jasmine.createSpyObj('UserManagementService', ['getUserProfile']);
    userManagement.getUserProfile.and.returnValue(of({ skills: ['java', 'Angular'] } as UserProfile));
    const reports = jasmine.createSpyObj('ActivityReportService', ['getReports', 'getReport']);
    reports.getReports.and.returnValue(of([]));
    reports.getReport.and.returnValue(EMPTY);
    params = new BehaviorSubject<Record<string, string>>({ id: '12' });
    user = { id: 7 };
    TestBed.configureTestingModule({
      imports: [ProjectDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params, snapshot: { queryParamMap: convertToParamMap(query) } } },
        { provide: ProjectService, useValue: projectService },
        { provide: InterviewStepService, useValue: steps },
        { provide: ActivityReportService, useValue: reports },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog },
        { provide: UserManagementService, useValue: userManagement },
        { provide: AuthService, useValue: { getUser: () => user } }
      ]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    before();
    const fixture = TestBed.createComponent(ProjectDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProjectDetailComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function closingWith(result: unknown): ReturnType<MatDialog['open']> {
    return { afterClosed: () => of(result) } as ReturnType<MatDialog['open']>;
  }

  function button(fixture: ComponentFixture<ProjectDetailComponent>, label: string): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find(candidate => candidate.textContent!.includes(label))!;
  }

  it('shows everything known about the opportunity', () => {
    const fixture = create();
    const page = text(fixture);

    expect(projectService.getById).toHaveBeenCalledWith(12);
    expect(projectService.getProjectNotes).toHaveBeenCalledWith(12);
    for (const shown of ['Développeur Java', 'projects.statuses.INTERVIEW', 'projects.workModes.hybrid', 'Société Générale',
      'Free-Work', 'Automne', '650 €', 'Plateforme de paiement', 'Tickets restaurant', '800€', '150€', '(18.75%)', '700€',
      '620€', '216 common.days', 'Nov 2, 2026', '6 common.months', '3 common.months', '8 projects.detail.daysPerMonth',
      '12 projects.detail.daysPerMonth', '4/5', 'contrat.pdf', 'Rappeler jeudi', 'steps.summary', 'steps.empty',
      'projects.notes.empty']) {
      expect(page).withContext(shown).toContain(shown);
    }
    expect(fixture.nativeElement.querySelector('.client-link').getAttribute('href')).toBe('/clients/3');
    expect(fixture.nativeElement.querySelectorAll('.rating-display mat-icon.filled').length).toBe(4);
    expect(fixture.nativeElement.querySelector('app-activity-report-card')).toBeNull();
    // 650 €/day over 216 days a year for 6 months.
    expect(page).toContain('70,200€');
  });

  it('shows the opportunity\'s skills and how well they match the freelance\'s', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(component.getTechTags()).toEqual(['Java', 'Spring', 'Kafka']);
    expect(component.getMatchScore()).toBe(33);
    expect(fixture.nativeElement.querySelectorAll('.skill-tags mat-chip').length).toBe(3);
    expect(fixture.nativeElement.querySelector('.skill-match-value').textContent).toContain('33%');
  });

  it('keeps a match score of 0 when the freelance\'s skills cannot be loaded', () => {
    const fixture = create(mission, {}, () => userManagement.getUserProfile.and.returnValue(throwError(() => new Error('down'))));

    expect(fixture.componentInstance.getMatchScore()).toBe(0);
  });

  it('shows a won mission\'s activity report and says less about a sparse opportunity', () => {
    const fixture = create({
      id: 12, role: 'Mission', status: ProjectStatus.WON, dailyRate: 500, clientName: 'Acme', workMode: 'REMOTE'
    } as ProjectDto);
    const page = text(fixture);

    expect(fixture.nativeElement.querySelector('app-activity-report-card')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.client-link')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-project-resources')).toBeNull();
    expect(page).toContain('common.notSpecifiedFeminine');
    expect(page).toContain('common.notSpecifiedPlural');
    expect(page).toContain('common.notSpecified');
    expect(page).toContain('projects.workModes.remote');
    expect(fixture.componentInstance.calculateTotalRevenue()).toBe(0);
  });

  it('shows the reason an opportunity was lost', () => {
    const fixture = create({ ...mission, status: ProjectStatus.LOST, lostReason: 'RATE_TOO_LOW' });

    expect(text(fixture)).toContain('projects.lostReason.RATE_TOO_LOW');
  });

  it('goes back to the list of a project that does not exist', () => {
    create(null);

    expect(notifications.error).toHaveBeenCalledWith('errors.projectNotFound');
    expect(navigate).toHaveBeenCalledWith(['/projects']);
  });

  it('goes back to the list when the project cannot be loaded', () => {
    const consoleError = spyOn(console, 'error');
    const fixture = create(mission, {},
      () => projectService.getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 }))));

    expect(notifications.error).toHaveBeenCalledWith('errors.projectNotFound');
    expect(navigate).toHaveBeenCalledWith(['/projects']);
    expect(fixture.componentInstance.isLoading).toBeFalse();

    projectService.getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    params.next({ id: '13' });
    expect(notifications.error).toHaveBeenCalledWith('errors.loadingProject');
    expect(consoleError).toHaveBeenCalled();
  });

  it('loads nothing without a project id', () => {
    const fixture = create(mission, {}, () => params.next({}));

    expect(projectService.getById).not.toHaveBeenCalled();
    expect(fixture.componentInstance.project).toBeUndefined();
    fixture.componentInstance.openStepDialog();
    fixture.componentInstance.addNote();
    fixture.componentInstance.deleteNote(1);
    fixture.componentInstance.onEdit();
    fixture.componentInstance.onDelete();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(confirmDialog.confirm).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('opens the status menu when asked to change the status', async () => {
    const fixture = create(mission, { focus: 'status' });
    await fixture.whenStable();
    fixture.detectChanges();

    const items = document.querySelectorAll('.mat-mdc-menu-panel button[mat-menu-item]');
    expect(items.length).toBe(5);
  });

  describe('status', () => {
    it('moves the opportunity to another stage', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      projectService.updateStatus.and.returnValue(of({ ...mission, status: ProjectStatus.OFFER }));

      component.changeStatus(ProjectStatus.OFFER);

      expect(projectService.updateStatus).toHaveBeenCalledWith(12, ProjectStatus.OFFER, undefined);
      expect(component.project?.status).toBe(ProjectStatus.OFFER);
      expect(component.isUpdatingStatus).toBeFalse();
      expect(notifications.success).toHaveBeenCalledWith('projects.detail.statusUpdated', 2500,
        { status: 'projects.statuses.OFFER' });
      expect(text(fixture)).toContain('projects.statuses.OFFER');
    });

    it('asks why the opportunity was lost', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      dialog.open.and.returnValue(closingWith('POSITION_FILLED'));
      projectService.updateStatus.and.returnValue(of({ ...mission, status: ProjectStatus.LOST,
        lostReason: 'POSITION_FILLED' }));

      component.changeStatus(ProjectStatus.LOST);

      expect(dialog.open).toHaveBeenCalledWith(KanbanLostReasonDialogComponent, jasmine.objectContaining({
        data: { role: 'Développeur Java' }
      }));
      expect(projectService.updateStatus).toHaveBeenCalledWith(12, ProjectStatus.LOST, 'POSITION_FILLED');
      expect(component.project?.lostReason).toBe('POSITION_FILLED');
    });

    it('keeps the stage when no reason for the loss is given', () => {
      const component = create().componentInstance;
      dialog.open.and.returnValue(closingWith(undefined));

      component.changeStatus(ProjectStatus.LOST);

      expect(projectService.updateStatus).not.toHaveBeenCalled();
    });

    it('ignores the current stage, or a change while one is saved', () => {
      const component = create().componentInstance;

      component.changeStatus(ProjectStatus.INTERVIEW);
      component.isUpdatingStatus = true;
      component.changeStatus(ProjectStatus.WON);

      expect(projectService.updateStatus).not.toHaveBeenCalled();
    });

    it('reports a stage that could not be saved', () => {
      const component = create().componentInstance;
      projectService.updateStatus.and.returnValue(throwError(() => new Error('down')));

      component.changeStatus(ProjectStatus.WON);

      expect(notifications.error).toHaveBeenCalledWith('errors.movingCard');
      expect(component.isUpdatingStatus).toBeFalse();
      expect(component.project?.status).toBe(ProjectStatus.INTERVIEW);
    });
  });

  describe('renaming a skill', () => {
    it('renames the tag across projects and reloads the opportunity', () => {
      const fixture = create();
      dialog.open.and.returnValue(closingWith({ from: 'Kafka', to: 'Apache Kafka' }));
      projectService.renameTag.and.returnValue(of(4));

      (fixture.nativeElement.querySelector('.skill-rename-btn') as HTMLButtonElement).click();

      expect(dialog.open).toHaveBeenCalledWith(RenameTagDialogComponent, { data: { tags: ['Java', 'Spring', 'Kafka'] }, autoFocus: false });
      expect(projectService.renameTag).toHaveBeenCalledWith(7, 'Kafka', 'Apache Kafka');
      expect(notifications.success).toHaveBeenCalledWith('projects.skills.renameSuccess', 3000, { count: 4 });
      expect(projectService.getById).toHaveBeenCalledTimes(2);
    });

    it('renames nothing when cancelled', () => {
      const component = create().componentInstance;
      dialog.open.and.returnValue(closingWith(undefined));

      component.openRenameTag();

      expect(projectService.renameTag).not.toHaveBeenCalled();
    });

    it('reports a tag that could not be renamed', () => {
      const component = create().componentInstance;
      dialog.open.and.returnValue(closingWith({ from: 'Kafka', to: 'Apache Kafka' }));
      projectService.renameTag.and.returnValue(throwError(() => new Error('down')));

      component.openRenameTag();

      expect(notifications.error).toHaveBeenCalledWith('projects.skills.renameError');
    });

    it('offers nothing to rename without tags or a signed-in user', () => {
      const fixture = create({ ...mission, techStack: '' });
      const component = fixture.componentInstance;

      component.openRenameTag();
      expect(fixture.nativeElement.querySelector('.skill-rename-btn')).toBeNull();
      component.project = mission;
      user = null;
      component.openRenameTag();

      expect(dialog.open).not.toHaveBeenCalled();
    });
  });

  describe('navigation', () => {
    it('goes back to the list and to the edit form', () => {
      const fixture = create();

      button(fixture, 'common.back').click();
      expect(navigate).toHaveBeenCalledWith(['/projects']);
      button(fixture, 'common.edit').click();
      expect(navigate).toHaveBeenCalledWith(['/projects', 12, 'edit']);
    });

    it('deletes the opportunity once confirmed and goes back to the list', () => {
      const fixture = create();
      projectService.delete.and.returnValue(of(undefined));

      button(fixture, 'common.delete').click();

      expect(confirmDialog.confirm).toHaveBeenCalledWith({
        messageKey: 'projects.confirmDelete', messageParams: { name: 'Développeur Java' }, danger: true
      });
      expect(projectService.delete).toHaveBeenCalledWith(12);
      expect(notifications.success).toHaveBeenCalledWith('projects.deleteSuccess');
      expect(navigate).toHaveBeenCalledWith(['/projects']);
    });

    it('keeps the opportunity when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirmDialog.confirm.and.returnValue(of(false));

      component.onDelete();

      expect(projectService.delete).not.toHaveBeenCalled();
    });

    it('reports an opportunity that could not be deleted', () => {
      const consoleError = spyOn(console, 'error');
      const component = create().componentInstance;
      projectService.delete.and.returnValue(throwError(() => new Error('down')));

      component.onDelete();

      expect(notifications.error).toHaveBeenCalledWith('errors.deletingProject');
      expect(navigate).not.toHaveBeenCalled();
      expect(consoleError).toHaveBeenCalled();
    });
  });

  it('formats its values', () => {
    const component = create().componentInstance;

    expect(component.getWorkModeLabel('ONSITE')).toBe('projects.workModes.onsite');
    expect(component.getWorkModeLabel(undefined)).toBe('common.notSpecified');
    expect(component.getRatingStars(2)).toEqual(['star', 'star', 'star_border', 'star_border', 'star_border']);
    expect(component.getRatingStars()).toEqual(Array(5).fill('star_border'));
    expect(component.getStatusColor()).toBe(PROJECT_STATUS_COLORS[ProjectStatus.CONTACT]);
    expect(component.getStatusColor(ProjectStatus.LOST)).toBe(PROJECT_STATUS_COLORS[ProjectStatus.LOST]);
    expect(component.formatDate('2026-03-15')).toBe('Mar 15, 2026');
    expect(component.formatDate(undefined)).toBe('common.notSpecified');
    component.project = { ...mission, daysPerYear: undefined, durationInMonths: 12 };
    expect(component.calculateTotalRevenue()).toBe(650 * 220);
  });
});

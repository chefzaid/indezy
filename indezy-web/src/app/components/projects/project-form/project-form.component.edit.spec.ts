import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ClientDto, ProjectDto, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { ProjectService } from '../../../services/project/project.service';
import { SeasonService } from '../../../services/season/season.service';
import { SourceService } from '../../../services/source/source.service';
import { ProjectFormComponent } from './project-form.component';

describe('ProjectFormComponent editing, errors and helpers', () => {
  const saved: ProjectDto = {
    id: 40, role: 'Développeur Java', dailyRate: 600, clientId: 1, freelanceId: 3, status: ProjectStatus.LOST,
    refusedBy: 'FREELANCE', lostReason: 'RATE_TOO_LOW', startDate: '2026-11-02', durationInMonths: 6, documents: ['contrat.pdf'], seasonId: 4,
    techStack: 'Java', isFavorite: true
  } as ProjectDto;

  let projects: jasmine.SpyObj<ProjectService>;
  let clients: jasmine.SpyObj<ClientService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let navigate: jasmine.Spy;

  interface Options {
    id?: number;
    query?: Record<string, string>;
    user?: { id: number } | null;
    before?: () => void;
  }

  function create({ id, query = {}, user = { id: 7 }, before }: Options = {}): ComponentFixture<ProjectFormComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['getById', 'create', 'update']);
    projects.getById.and.returnValue(of({ ...saved }));
    clients = jasmine.createSpyObj('ClientService', ['getByFreelanceId']);
    clients.getByFreelanceId.and.returnValue(of([{ id: 1, companyName: 'Airbus', city: 'Toulouse', isFinal: true }] as ClientDto[]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProjectFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: ActivatedRoute, useValue: { params: of(id ? { id } : {}), snapshot: { queryParamMap: convertToParamMap(query) } } },
        { provide: ProjectService, useValue: projects },
        { provide: OfferService, useValue: jasmine.createSpyObj('OfferService', ['importOffer', 'importPosting']) },
        { provide: ClientService, useValue: clients },
        { provide: SourceService, useValue: { getByFreelanceId: () => of([{ id: 2, name: 'Malt' }, { id: 1, name: 'Free-Work' }]) } },
        { provide: SeasonService, useValue: { getByFreelanceId: () => of([{ id: 4, name: 'Automne', startDate: '2026-09-01' }]) } },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    before?.();
    const fixture = TestBed.createComponent(ProjectFormComponent);
    fixture.detectChanges();
    return fixture;
  }

  function button(fixture: ComponentFixture<ProjectFormComponent>, label: string): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find(candidate => candidate.textContent!.includes(label))!;
  }

  describe('an existing project', () => {
    it('fills the form in with the project as saved', () => {
      const fixture = create({ id: 40 });
      const component = fixture.componentInstance;

      expect(projects.getById).toHaveBeenCalledWith(40);
      expect(component.isEditMode).toBeTrue();
      expect(component.isLoading).toBeFalse();
      expect(component.projectForm.value).toEqual(jasmine.objectContaining({
        role: 'Développeur Java', dailyRate: 600, clientId: 1, status: ProjectStatus.LOST, refusedBy: 'FREELANCE',
        lostReason: 'RATE_TOO_LOW', seasonId: 4, isFavorite: true, middlemanId: null, sourceId: null
      }));
      expect((component.projectForm.value.startDate as Date).getDate()).toBe(2);
      expect(component.pageTitle).toBe('projects.form.editTitle');
      expect(component.submitButtonText).toBe('common.edit');
      expect(fixture.nativeElement.textContent).toContain('projects.form.editTitle');
    });

    it('defaults the fields a project saved without them lacks', () => {
      const fixture = create({ id: 41, before: () => projects.getById.and.returnValue(of({ id: 41, role: 'Ancienne mission', dailyRate: 400 } as ProjectDto)) });

      expect(fixture.componentInstance.projectForm.value).toEqual(jasmine.objectContaining({
        status: ProjectStatus.CONTACT, refusedBy: null, lostReason: null, clientId: null, seasonId: null, isFavorite: false
      }));
    });

    it('saves the changes, keeping what the form does not edit', () => {
      const fixture = create({ id: 40 });
      const component = fixture.componentInstance;
      projects.update.and.returnValue(of({ ...saved, role: 'Tech lead Java' }));
      component.projectForm.patchValue({ role: 'Tech lead Java', description: '' });

      button(fixture, 'common.edit').click();

      const [projectId, sent] = projects.update.calls.mostRecent().args;
      expect(projectId).toBe(40);
      expect(sent).toEqual(jasmine.objectContaining({
        id: 40, role: 'Tech lead Java', documents: ['contrat.pdf'], freelanceId: 3, startDate: '2026-11-02',
        status: ProjectStatus.LOST, refusedBy: 'FREELANCE', lostReason: 'RATE_TOO_LOW'
      }));
      expect(notifications.success).toHaveBeenCalledWith('projects.updateSuccess');
      expect(navigate).toHaveBeenCalledWith(['/projects', 40]);
    });

    it('forgets who refused it and why once it is back in the pipeline', () => {
      const component = create({ id: 40 }).componentInstance;
      projects.update.and.returnValue(of({} as ProjectDto));

      component.projectForm.patchValue({ status: ProjectStatus.OFFER });
      expect(component.projectForm.value.refusedBy).toBeNull();
      expect(component.projectForm.value.lostReason).toBeNull();
      component.onSubmit();

      expect(projects.update.calls.mostRecent().args[1].refusedBy).toBeUndefined();
      expect(projects.update.calls.mostRecent().args[1].lostReason).toBeUndefined();
      expect(navigate).toHaveBeenCalledWith(['/projects', 40]);
    });

    it('keeps who refused it and why while the project stays refused', () => {
      const component = create({ id: 40 }).componentInstance;

      component.projectForm.patchValue({ status: ProjectStatus.LOST });

      expect(component.projectForm.value.refusedBy).toBe('FREELANCE');
      expect(component.projectForm.value.lostReason).toBe('RATE_TOO_LOW');
    });

    it('asks who refused a refused project before saving it, the reason being optional', () => {
      const fixture = create({ id: 42, before: () => projects.getById.and.returnValue(of({ ...saved, id: 42, refusedBy: undefined,
        lostReason: undefined } as ProjectDto)) });
      const component = fixture.componentInstance;
      projects.update.and.returnValue(of({} as ProjectDto));

      component.onSubmit();
      expect(projects.update).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-refused-by-field]')).not.toBeNull();

      component.projectForm.patchValue({ refusedBy: 'RECRUITER' });
      component.onSubmit();

      expect(projects.update.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
        status: ProjectStatus.LOST, refusedBy: 'RECRUITER', lostReason: undefined }));
    });

    it('reports changes that could not be saved', () => {
      const consoleError = spyOn(console, 'error');
      const component = create({ id: 40 }).componentInstance;
      projects.update.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.updatingProject');
      expect(component.isSubmitting).toBeFalse();
      expect(navigate).not.toHaveBeenCalled();
      expect(consoleError).toHaveBeenCalled();
    });

    it('goes back to the project when cancelled', () => {
      const fixture = create({ id: 40 });

      button(fixture, 'common.cancel').click();

      expect(navigate).toHaveBeenCalledWith(['/projects', 40]);
    });

    it('goes back to the list of a project that does not exist', () => {
      create({ id: 40, before: () => projects.getById.and.returnValue(of(null as unknown as ProjectDto)) });

      expect(notifications.error).toHaveBeenCalledWith('errors.projectNotFound');
      expect(navigate).toHaveBeenCalledWith(['/projects']);
    });

    it('goes back to the list when the project cannot be loaded', () => {
      const consoleError = spyOn(console, 'error');
      const fixture = create({ id: 40,
        before: () => projects.getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 }))) });
      expect(notifications.error).toHaveBeenCalledWith('errors.projectNotFound');
      expect(fixture.componentInstance.isLoading).toBeFalse();
      TestBed.resetTestingModule();

      create({ id: 40, before: () => projects.getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 }))) });

      expect(notifications.error).toHaveBeenCalledWith('errors.loadingProject');
      expect(navigate).toHaveBeenCalledWith(['/projects']);
      expect(consoleError).toHaveBeenCalled();
    });
  });

  describe('a new opportunity', () => {
    it('starts at the stage asked for, a known one only', () => {
      expect(create({ query: { status: ProjectStatus.OFFER } }).componentInstance.projectForm.value.status).toBe(ProjectStatus.OFFER);
      TestBed.resetTestingModule();
      expect(create({ query: { status: 'SIGNED' } }).componentInstance.projectForm.value.status).toBe(ProjectStatus.CONTACT);
    });

    it('lists the sources by name, joins no season when none is running, and titles the page for a creation', () => {
      const component = create().componentInstance;

      expect(component.projectForm.value.seasonId).toBeNull();
      expect(component.sources.map(source => source.name)).toEqual(['Free-Work', 'Malt']);
      expect(component.pageTitle).toBe('projects.form.newTitle');
      expect(component.submitButtonText).toBe('common.create');
    });

    it('explains what is missing instead of saving', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      component.projectForm.patchValue({ role: 'X', dailyRate: 0 });

      component.onSubmit();
      fixture.detectChanges();

      expect(projects.create).not.toHaveBeenCalled();
      expect(component.getFieldError('role')).toBe('errors.minLength');
      expect(component.getFieldError('dailyRate')).toBe('errors.minValue');
      expect(component.getFieldError('clientId')).toBe('errors.fieldRequired');
      expect(component.getFieldError('description')).toBe('');
      expect(fixture.nativeElement.querySelectorAll('mat-error').length).toBeGreaterThan(0);
    });

    it('saves once even when submitted twice', () => {
      const component = create().componentInstance;
      component.projectForm.patchValue({ role: 'Architecte', dailyRate: 700, clientId: 1 });
      component.isSubmitting = true;

      component.onSubmit();

      expect(projects.create).not.toHaveBeenCalled();
    });

    it('goes back to the list when saved without an id, or cancelled', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      projects.create.and.returnValue(of({} as ProjectDto));
      component.projectForm.patchValue({ role: 'Architecte', dailyRate: 700, clientId: 1 });

      component.onSubmit();
      expect(notifications.success).toHaveBeenCalledWith('projects.createSuccess');
      expect(navigate).toHaveBeenCalledWith(['/projects']);

      navigate.calls.reset();
      component.onCancel();
      expect(navigate).toHaveBeenCalledWith(['/projects']);
    });

    it('reports an opportunity that could not be created, even a duplicate one', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      projects.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
      component.projectForm.patchValue({ role: 'Architecte', dailyRate: 700, clientId: 1 });

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.creatingProject');
      expect(component.isSubmitting).toBeFalse();
    });

    it('reports clients and sources that could not be loaded', () => {
      const consoleError = spyOn(console, 'error');
      const component = create({ before: () => clients.getByFreelanceId.and.returnValue(throwError(() => new Error('down'))) })
        .componentInstance;

      expect(notifications.error).toHaveBeenCalledWith('errors.loadingClients');
      expect(component.clients).toEqual([]);
      expect(consoleError).toHaveBeenCalled();
    });

    it('loads no clients without a signed-in user', () => {
      const component = create({ user: null }).componentInstance;

      expect(clients.getByFreelanceId).not.toHaveBeenCalled();
      expect(component.projectForm.value.seasonId).toBeNull();
    });
  });

  describe('maximum workable days', () => {
    afterEach(() => jasmine.clock().uninstall());

    it('counts the business days of the year without French public holidays', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(2026, 2, 15));

      (fixture.nativeElement.querySelector('.max-days-link') as HTMLElement).click();

      expect(component.projectForm.value.daysPerYear).toBe(252);
    });

    it('counts the next year from November on', () => {
      const component = create().componentInstance;
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(2026, 10, 3));

      component.setMaxWorkableDays();

      expect(component.projectForm.value.daysPerYear).toBe(254);
    });
  });
});

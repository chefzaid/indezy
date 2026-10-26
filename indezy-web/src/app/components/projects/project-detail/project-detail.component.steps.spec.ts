import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InterviewStepDto, ProjectDto, ProjectNote, ProjectStatus, StepStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { UserProfile } from '../../../models/user-management.models';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { InterviewStepDialogComponent } from '../interview-step-dialog/interview-step-dialog.component';
import { ProjectDetailComponent } from './project-detail.component';

describe('ProjectDetailComponent steps and journal', () => {
  const mission = { id: 12, role: 'Développeur Java', status: ProjectStatus.INTERVIEW, dailyRate: 650, clientName: 'Acme',
    totalSteps: 2, completedSteps: 1 } as ProjectDto;
  const call: InterviewStepDto = { id: 1, title: 'Appel RH', date: '2026-10-20T10:00:00', status: StepStatus.VALIDATED,
    notes: 'Très positif', projectId: 12 };
  const interview: InterviewStepDto = { id: 2, title: 'Entretien technique', status: StepStatus.TO_PLAN, projectId: 12 };
  const notes: ProjectNote[] = [
    { id: 5, projectId: 12, content: '**Relance** envoyée', createdAt: '2026-10-21T09:30:00' },
    { id: 4, projectId: 12, content: 'Premier contact', createdAt: '2026-10-19T14:00:00' }
  ];

  let projectService: jasmine.SpyObj<ProjectService>;
  let stepService: jasmine.SpyObj<InterviewStepService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  function create(before: () => void = () => undefined): ComponentFixture<ProjectDetailComponent> {
    projectService = jasmine.createSpyObj('ProjectService', ['getById', 'getProjectNotes', 'addProjectNote', 'deleteProjectNote']);
    projectService.getById.and.returnValue(of(mission));
    projectService.getProjectNotes.and.returnValue(of([...notes]));
    stepService = jasmine.createSpyObj('InterviewStepService', ['getByProjectIdOrderByDate', 'updateStatus', 'delete']);
    stepService.getByProjectIdOrderByDate.and.returnValue(of([call, interview]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmDialog.confirm.and.returnValue(of(true));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [ProjectDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: of({ id: '12' }), snapshot: { queryParamMap: convertToParamMap({}) } } },
        { provide: ProjectService, useValue: projectService },
        { provide: InterviewStepService, useValue: stepService },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog },
        { provide: UserManagementService, useValue: { getUserProfile: () => of({} as UserProfile) } },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } }
      ]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
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

  describe('interview steps', () => {
    it('lists the steps in a timeline, the validated ones done', () => {
      const fixture = create();
      const items: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.step-item'));

      expect(stepService.getByProjectIdOrderByDate).toHaveBeenCalledWith(12);
      expect(items.length).toBe(2);
      expect(items[0].classList).toContain('done');
      expect(items[1].classList).not.toContain('done');
      expect(items[0].textContent).toContain('Appel RH');
      expect(items[0].textContent).toContain('Très positif');
      expect(items[0].textContent).toContain('20 October 2026, 10:00');
      expect(items[1].textContent).toContain('steps.noDate');
      expect(fixture.componentInstance.isStepDone(interview)).toBeFalse();
    });

    it('reports steps that could not be loaded', () => {
      create(() => stepService.getByProjectIdOrderByDate.and.returnValue(throwError(() => new Error('down'))));

      expect(notifications.error).toHaveBeenCalledWith('steps.loadError');
    });

    it('adds a step, then reloads the steps and their counters', () => {
      const fixture = create();
      dialog.open.and.returnValue(closingWith(true));

      (fixture.nativeElement.querySelector('.add-step-btn') as HTMLButtonElement).click();

      expect(dialog.open).toHaveBeenCalledWith(InterviewStepDialogComponent, {
        data: { projectId: 12, step: undefined }, autoFocus: 'first-tabbable'
      });
      expect(stepService.getByProjectIdOrderByDate).toHaveBeenCalledTimes(2);
      expect(projectService.getById).toHaveBeenCalledTimes(2);
    });

    it('edits a step and reloads nothing when the dialog is dismissed', () => {
      const fixture = create();
      dialog.open.and.returnValue(closingWith(undefined));

      fixture.componentInstance.openStepDialog(call);

      expect(dialog.open).toHaveBeenCalledWith(InterviewStepDialogComponent, jasmine.objectContaining({
        data: { projectId: 12, step: call }
      }));
      expect(stepService.getByProjectIdOrderByDate).toHaveBeenCalledTimes(1);
    });

    it('changes the status of a step', () => {
      const fixture = create();
      stepService.updateStatus.and.returnValue(of({ ...interview, status: StepStatus.PLANNED }));
      projectService.getById.and.returnValue(of({ ...mission, totalSteps: 3 }));

      fixture.componentInstance.setStepStatus(interview, StepStatus.PLANNED);

      expect(stepService.updateStatus).toHaveBeenCalledWith(2, StepStatus.PLANNED);
      expect(notifications.success).toHaveBeenCalledWith('steps.statusUpdated', 2000);
      expect(fixture.componentInstance.project?.totalSteps).toBe(3);
    });

    it('keeps a step without id or already in that status', () => {
      const component = create().componentInstance;

      component.setStepStatus(call, StepStatus.VALIDATED);
      component.setStepStatus({ ...interview, id: undefined }, StepStatus.PLANNED);
      component.deleteStep({ ...interview, id: undefined });

      expect(stepService.updateStatus).not.toHaveBeenCalled();
      expect(confirmDialog.confirm).not.toHaveBeenCalled();
    });

    it('reports a step status that could not be saved', () => {
      const component = create().componentInstance;
      stepService.updateStatus.and.returnValue(throwError(() => new Error('down')));

      component.setStepStatus(interview, StepStatus.CANCELED);

      expect(notifications.error).toHaveBeenCalledWith('errors.updatingStep');
    });

    it('deletes a step once confirmed', () => {
      const fixture = create();
      stepService.delete.and.returnValue(of(undefined));
      stepService.getByProjectIdOrderByDate.and.returnValue(of([call]));

      const deleteButton = fixture.nativeElement.querySelectorAll('.step-actions button')[3] as HTMLButtonElement;
      deleteButton.click();

      expect(confirmDialog.confirm).toHaveBeenCalledWith({
        messageKey: 'steps.deleteConfirm', messageParams: { title: 'Entretien technique' }, danger: true
      });
      expect(stepService.delete).toHaveBeenCalledWith(2);
      expect(notifications.success).toHaveBeenCalledWith('steps.deletedSuccess', 2000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.step-item').length).toBe(1);
    });

    it('keeps a step when its deletion is not confirmed, and reports a failed one', () => {
      const component = create().componentInstance;
      confirmDialog.confirm.and.returnValue(of(false));
      component.deleteStep(call);
      expect(stepService.delete).not.toHaveBeenCalled();

      confirmDialog.confirm.and.returnValue(of(true));
      stepService.delete.and.returnValue(throwError(() => new Error('down')));
      component.deleteStep(call);
      expect(notifications.error).toHaveBeenCalledWith('errors.updatingStep');
    });
  });

  describe('journal', () => {
    it('lists the notes rendered from Markdown', () => {
      const fixture = create();
      const entries: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.journal-entry'));

      expect(entries.length).toBe(2);
      expect(entries[0].querySelector('.journal-content strong')?.textContent).toBe('Relance');
      expect(entries[1].textContent).toContain('Premier contact');
      expect(text(fixture)).not.toContain('projects.notes.empty');
    });

    it('reports notes that could not be loaded', () => {
      create(() => projectService.getProjectNotes.and.returnValue(throwError(() => new Error('down'))));

      expect(notifications.error).toHaveBeenCalledWith('projects.notes.loadError');
    });

    it('previews and adds a note at the top of the journal', async () => {
      const fixture = create();
      const component = fixture.componentInstance;
      const added: ProjectNote = { id: 6, projectId: 12, content: 'Proposition *reçue*', createdAt: '2026-10-22T08:00:00' };
      projectService.addProjectNote.and.returnValue(of(added));
      const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('.journal-input textarea');

      textarea.value = '  Proposition *reçue*  ';
      textarea.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('.journal-preview em')?.textContent).toBe('reçue');
      (fixture.nativeElement.querySelector('.journal-add > button') as HTMLButtonElement).click();

      expect(projectService.addProjectNote).toHaveBeenCalledWith(12, 'Proposition *reçue*');
      expect(component.notes[0]).toEqual(added);
      expect(component.newNoteContent).toBe('');
      expect(component.isSavingNote).toBeFalse();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.journal-entry').length).toBe(3);
    });

    it('adds no blank note, nor one while another is saved', () => {
      const component = create().componentInstance;

      component.newNoteContent = '   ';
      component.addNote();
      component.newNoteContent = 'Relancer';
      component.isSavingNote = true;
      component.addNote();

      expect(projectService.addProjectNote).not.toHaveBeenCalled();
    });

    it('keeps a note that could not be saved', () => {
      const component = create().componentInstance;
      projectService.addProjectNote.and.returnValue(throwError(() => new Error('down')));
      component.newNoteContent = 'Relancer';

      component.addNote();

      expect(notifications.error).toHaveBeenCalledWith('projects.notes.saveError');
      expect(component.newNoteContent).toBe('Relancer');
      expect(component.isSavingNote).toBeFalse();
    });

    it('deletes a note once confirmed', () => {
      const fixture = create();
      projectService.deleteProjectNote.and.returnValue(of(undefined));

      (fixture.nativeElement.querySelector('.journal-delete') as HTMLButtonElement).click();

      expect(confirmDialog.confirm).toHaveBeenCalledWith({ messageKey: 'projects.notes.deleteConfirm', danger: true });
      expect(projectService.deleteProjectNote).toHaveBeenCalledWith(12, 5);
      expect(fixture.componentInstance.notes.map(note => note.id)).toEqual([4]);
    });

    it('keeps a note when its deletion is not confirmed, and reports a failed one', () => {
      const component = create().componentInstance;
      confirmDialog.confirm.and.returnValue(of(false));
      component.deleteNote(5);
      expect(projectService.deleteProjectNote).not.toHaveBeenCalled();

      confirmDialog.confirm.and.returnValue(of(true));
      projectService.deleteProjectNote.and.returnValue(throwError(() => new Error('down')));
      component.deleteNote(5);
      expect(notifications.error).toHaveBeenCalledWith('projects.notes.deleteError');
      expect(component.notes.length).toBe(2);
    });

    it('inserts a note template, after what is already typed', () => {
      const component = create().componentInstance;

      component.selectedNoteTemplate = 'callDebrief';
      component.applyNoteTemplate('callDebrief');
      expect(component.newNoteContent).toBe('projects.notes.templates.callDebrief.body');
      expect(component.selectedNoteTemplate).toBe('');

      component.newNoteContent = 'Appel de 10h  \n';
      component.applyNoteTemplate('negotiationSummary');
      expect(component.newNoteContent).toBe('Appel de 10h\n\nprojects.notes.templates.negotiationSummary.body');

      component.applyNoteTemplate('');
      expect(component.newNoteContent).toBe('Appel de 10h\n\nprojects.notes.templates.negotiationSummary.body');
    });
  });
});

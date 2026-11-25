import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiDebrief, InterviewStepDto, ProjectDto, ProjectNote, ProjectStatus, StepStatus } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { DictationHandlers, DictationService } from '../../services/dictation.service';
import { AiDebriefDialogComponent, DEBRIEF_MAX } from './ai-debrief-dialog.component';

describe('AiDebriefDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let steps: jasmine.SpyObj<InterviewStepService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<AiDebriefDialogComponent, boolean>>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let dictation: jasmine.SpyObj<DictationService>;
  let handlers: DictationHandlers | null;
  let listening: boolean;
  const debrief: AiDebrief = { outcome: 'NEXT_ROUND', reason: null, nextRound: 'Entretien technique', nextSteps: ['Envoyer deux références'],
    thankYouSubject: "Merci pour l'échange", thankYouBody: 'Bonjour,\nmerci pour ce temps.' };

  let dialog: jasmine.SpyObj<MatDialog>;

  function create(status?: ProjectStatus, project?: ProjectDto) {
    ai = jasmine.createSpyObj('AiService', ['debrief']);
    steps = jasmine.createSpyObj('InterviewStepService', ['create']);
    projects = jasmine.createSpyObj('ProjectService', ['addProjectNote', 'updateStatus']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    handlers = null;
    listening = false;
    dictation = jasmine.createSpyObj('DictationService', ['supported', 'listening', 'start', 'stop']);
    dictation.supported.and.returnValue(true);
    dictation.listening.and.callFake(() => listening);
    dictation.start.and.callFake((_language: string, given: DictationHandlers) => {
      handlers = given;
      listening = true;
      return true;
    });
    dictation.stop.and.callFake(() => {
      listening = false;
    });
    TestBed.configureTestingModule({
      imports: [AiDebriefDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: InterviewStepService, useValue: steps }, { provide: ProjectService, useValue: projects },
        { provide: NotificationService, useValue: notifications }, { provide: Clipboard, useValue: clipboard },
        { provide: MatDialogRef, useValue: dialogRef }, { provide: DictationService, useValue: dictation }, { provide: MAT_DIALOG_DATA, useValue: { projectId: 9, status, project } }]
    });
    // The component's own dialog module provides the dialog service: replaced where it is looked for.
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(AiDebriefDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  function write(fixture: ReturnType<typeof create>, notes: string): HTMLElement {
    const element = fixture.nativeElement as HTMLElement;
    const area = element.querySelector('[data-ai-debrief-notes]') as HTMLTextAreaElement;
    area.value = notes;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    return element;
  }

  it('reads nothing until a debrief is written, then shows what comes next', () => {
    const fixture = create();
    let element = write(fixture, '   ');
    expect((element.querySelector('[data-ai-debrief-read]') as HTMLButtonElement).disabled).toBeTrue();
    fixture.componentInstance.read();
    expect(ai.debrief).not.toHaveBeenCalled();

    ai.debrief.and.returnValue(of(debrief));
    element = write(fixture, '  Bon feeling, entretien technique la semaine prochaine.  ');
    (element.querySelector('[data-ai-debrief-read]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(ai.debrief).toHaveBeenCalledWith(9, 'Bon feeling, entretien technique la semaine prochaine.', 'FR');
    expect(element.querySelector('[data-ai-debrief-outcome="NEXT_ROUND"]')).not.toBeNull();
    expect(element.querySelector('[data-ai-debrief-steps]')!.textContent).toContain('Envoyer deux références');
    expect(element.querySelector('[data-ai-debrief-thanks]')!.textContent).toContain('merci pour ce temps');
    expect(fixture.componentInstance.max).toBe(DEBRIEF_MAX);

    (element.querySelector('[data-ai-debrief-copy]') as HTMLButtonElement).click();
    expect(clipboard.copy).toHaveBeenCalledWith("Merci pour l'échange\n\nBonjour,\nmerci pour ce temps.");
    expect(notifications.success).toHaveBeenCalledWith('ai.debrief.copied');

    // Back to the debrief, kept as written.
    (element.querySelector('[data-ai-debrief-edit]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((element.querySelector('[data-ai-debrief-notes]') as HTMLTextAreaElement).value).toContain('Bon feeling');
  });

  it('adds the next round as a step to plan once, and says so as it closes', () => {
    const fixture = create();
    ai.debrief.and.returnValue(of(debrief));
    const element = write(fixture, 'Entretien technique annoncé.');
    fixture.componentInstance.read();
    fixture.detectChanges();

    steps.create.and.returnValue(throwError(() => new Error('down')));
    (element.querySelector('[data-ai-debrief-round]') as HTMLButtonElement).click();
    expect(notifications.error).toHaveBeenCalledWith('ai.debrief.roundError');
    fixture.componentInstance.close();
    expect(dialogRef.close).toHaveBeenCalledWith(false);

    steps.create.and.returnValue(of({ id: 3 } as InterviewStepDto));
    (element.querySelector('[data-ai-debrief-round]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(steps.create).toHaveBeenCalledWith({ projectId: 9, title: 'Entretien technique', status: StepStatus.TO_PLAN });
    expect((element.querySelector('[data-ai-debrief-round]') as HTMLButtonElement).disabled).toBeTrue();
    fixture.componentInstance.addRound('Entretien technique');
    expect(steps.create).toHaveBeenCalledTimes(2);
    fixture.componentInstance.close();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('keeps the debrief and what comes of it as a note, and says when that fails', () => {
    const fixture = create();
    fixture.componentInstance.keepAsNote();
    expect(projects.addProjectNote).not.toHaveBeenCalled();
    ai.debrief.and.returnValue(of({ ...debrief, outcome: 'REJECTED', nextRound: null, nextSteps: [], thankYouBody: '' }));
    const element = write(fixture, 'Refusé, profil trop junior.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    expect(element.querySelector('[data-ai-debrief-round]')).toBeNull();
    expect(element.querySelector('[data-ai-debrief-thanks]')).toBeNull();

    projects.addProjectNote.and.returnValue(of({} as ProjectNote));
    (element.querySelector('[data-ai-debrief-note]') as HTMLButtonElement).click();
    const note = projects.addProjectNote.calls.mostRecent().args[1];
    expect(note).toContain('## ai.debrief.title');
    expect(note).toContain('Refusé, profil trop junior.');
    expect(note).toContain('**ai.debrief.outcomes.REJECTED**');
    expect(notifications.success).toHaveBeenCalledWith('ai.debrief.kept');

    ai.debrief.and.returnValue(of(debrief));
    fixture.componentInstance.read();
    projects.addProjectNote.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.keepAsNote();
    expect(projects.addProjectNote.calls.mostRecent().args[1]).toContain('- [ ] Envoyer deux références');
    expect(notifications.error).toHaveBeenCalledWith('ai.debrief.keepError');
  });

  it('dictates the debrief after what is typed, within its length, and stops listening to read it', () => {
    const fixture = create();
    const element = write(fixture, 'Bien passé.');
    (element.querySelector('[data-ai-debrief-dictate]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(dictation.start).toHaveBeenCalled();
    expect(element.querySelector('[data-ai-debrief-dictate]')!.getAttribute('aria-pressed')).toBe('true');
    handlers!.interim('entretien tech');
    fixture.detectChanges();
    expect(element.querySelector('[data-ai-debrief-hearing]')!.textContent).toContain('entretien tech');
    handlers!.final('Entretien technique lundi.');
    handlers!.final('');
    expect(fixture.componentInstance.notes.value).toBe('Bien passé. Entretien technique lundi.');
    handlers!.final('x'.repeat(DEBRIEF_MAX));
    expect(fixture.componentInstance.notes.value.length).toBe(DEBRIEF_MAX);

    ai.debrief.and.returnValue(of(debrief));
    fixture.componentInstance.read();
    expect(dictation.stop).toHaveBeenCalled();
    handlers!.ended('not-allowed');
    expect(notifications.error).toHaveBeenCalledWith('projects.notes.dictationDenied');
    handlers!.ended('network');
    expect(notifications.error).toHaveBeenCalledWith('projects.notes.dictationFailed');
    handlers!.ended('no-speech');
    expect(notifications.error).toHaveBeenCalledTimes(2);

    // A second press stops it; closing the dialog stops it too.
    fixture.componentInstance.edit();
    fixture.detectChanges();
    listening = true;
    fixture.componentInstance.toggleDictation();
    expect(listening).toBeFalse();
    fixture.destroy();
    expect(dictation.stop).toHaveBeenCalledTimes(3);
  });

  it('offers to move the opportunity to the stage the outcome points to, from a stage it may come from only', () => {
    const read = (outcome: AiDebrief['outcome'], status?: ProjectStatus): HTMLElement => {
      TestBed.resetTestingModule();
      const fixture = create(status);
      ai.debrief.and.returnValue(of({ ...debrief, outcome }));
      write(fixture, 'Compte rendu.');
      fixture.componentInstance.read();
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };
    expect(read('REJECTED', ProjectStatus.INTERVIEW).querySelector('[data-ai-debrief-move]')!.textContent).toContain('dashboard.suggestions.move');
    expect(read('REJECTED', ProjectStatus.LOST).querySelector('[data-ai-debrief-move]')).toBeNull();
    expect(read('OFFER', ProjectStatus.OFFER).querySelector('[data-ai-debrief-move]')).toBeNull();
    expect(read('NEXT_ROUND', ProjectStatus.INTERVIEW).querySelector('[data-ai-debrief-move]')).toBeNull();
    expect(read('WAITING', ProjectStatus.CONTACT).querySelector('[data-ai-debrief-move]')).toBeNull();
    expect(read('OFFER').querySelector('[data-ai-debrief-move]')).toBeNull();

    TestBed.resetTestingModule();
    const fixture = create(ProjectStatus.INTERVIEW);
    ai.debrief.and.returnValue(of({ ...debrief, outcome: 'OFFER' }));
    const element = write(fixture, 'Une proposition arrive.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    projects.updateStatus.and.returnValue(throwError(() => new Error('down')));
    (element.querySelector('[data-ai-debrief-move]') as HTMLButtonElement).click();
    expect(notifications.error).toHaveBeenCalledWith('dashboard.suggestions.error');
    projects.updateStatus.and.returnValue(of({} as ProjectDto));
    (element.querySelector('[data-ai-debrief-move]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(projects.updateStatus).toHaveBeenCalledWith(9, ProjectStatus.OFFER, undefined);
    expect(element.querySelector('[data-ai-debrief-move]')!.textContent).toContain('ai.debrief.moved');
    fixture.componentInstance.moveTo(ProjectStatus.OFFER);
    expect(projects.updateStatus).toHaveBeenCalledTimes(2);
    fixture.componentInstance.close();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('abandons an opportunity turned down for the reason the debrief gives, and shows it', () => {
    const fixture = create(ProjectStatus.INTERVIEW);
    ai.debrief.and.returnValue(of({ ...debrief, outcome: 'REJECTED', reason: 'RATE_TOO_LOW', nextRound: null }));
    const element = write(fixture, 'Trop cher pour eux.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    expect(element.querySelector('[data-ai-debrief-reason]')!.textContent).toContain('projects.lostReason.RATE_TOO_LOW');
    projects.updateStatus.and.returnValue(of({} as ProjectDto));
    (element.querySelector('[data-ai-debrief-move]') as HTMLButtonElement).click();
    expect(projects.updateStatus).toHaveBeenCalledWith(9, ProjectStatus.LOST, { lostReason: 'RATE_TOO_LOW' });
  });

  it('writes the thank-you as the opportunity\'s email, when the opportunity is known', () => {
    let fixture = create(ProjectStatus.INTERVIEW);
    ai.debrief.and.returnValue(of(debrief));
    write(fixture, 'Bien passé.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-ai-debrief-write]')).toBeNull();

    TestBed.resetTestingModule();
    const project = { id: 9, role: 'Lead Java', dailyRate: 650 } as ProjectDto;
    fixture = create(ProjectStatus.INTERVIEW, project);
    ai.debrief.and.returnValue(of(debrief));
    const element = write(fixture, 'Bien passé.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    (element.querySelector('[data-ai-debrief-write]') as HTMLButtonElement).click();
    expect(dialog.open).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({ data: jasmine.objectContaining({ id: 9,
      draft: { subject: "Merci pour l'échange", body: 'Bonjour,\nmerci pour ce temps.' } }) }));
  });

  it('says why the debrief could not be read', () => {
    const fixture = create();
    ai.debrief.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const element = write(fixture, 'Bien passé.');
    fixture.componentInstance.read();
    fixture.detectChanges();
    expect(element.querySelector('.error')).not.toBeNull();
    expect(element.querySelector('[data-ai-debrief-notes]')).not.toBeNull();
  });
});

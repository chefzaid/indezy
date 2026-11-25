import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiDebrief, InterviewStepDto, ProjectNote, StepStatus } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { AiDebriefDialogComponent, DEBRIEF_MAX } from './ai-debrief-dialog.component';

describe('AiDebriefDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let steps: jasmine.SpyObj<InterviewStepService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<AiDebriefDialogComponent, boolean>>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  const debrief: AiDebrief = { outcome: 'NEXT_ROUND', nextRound: 'Entretien technique', nextSteps: ['Envoyer deux références'],
    thankYouSubject: "Merci pour l'échange", thankYouBody: 'Bonjour,\nmerci pour ce temps.' };

  function create() {
    ai = jasmine.createSpyObj('AiService', ['debrief']);
    steps = jasmine.createSpyObj('InterviewStepService', ['create']);
    projects = jasmine.createSpyObj('ProjectService', ['addProjectNote']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [AiDebriefDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: InterviewStepService, useValue: steps }, { provide: ProjectService, useValue: projects },
        { provide: NotificationService, useValue: notifications }, { provide: Clipboard, useValue: clipboard },
        { provide: MatDialogRef, useValue: dialogRef }, { provide: MAT_DIALOG_DATA, useValue: { projectId: 9 } }]
    });
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

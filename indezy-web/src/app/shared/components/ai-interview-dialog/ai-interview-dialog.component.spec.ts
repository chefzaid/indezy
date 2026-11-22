import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ProjectNote } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { AiInterviewDialogComponent } from './ai-interview-dialog.component';

describe('AiInterviewDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const preparation = { technical: [{ question: 'Kafka exactly-once ?', hint: 'Idempotence.' }], behavioural: [{ question: 'Un conflit ?', hint: '' }],
    pitch: 'Bonjour, je suis Alice.', toAsk: ["Taille de l'équipe ?"] };

  function create(setup: () => void) {
    ai = jasmine.createSpyObj('AiService', ['interview']);
    projects = jasmine.createSpyObj('ProjectService', ['addProjectNote']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    const clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    setup();
    TestBed.configureTestingModule({
      imports: [AiInterviewDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: ProjectService, useValue: projects }, { provide: NotificationService, useValue: notifications },
        { provide: Clipboard, useValue: clipboard }, { provide: MAT_DIALOG_DATA, useValue: { projectId: 9 } }]
    });
    const fixture = TestBed.createComponent(AiInterviewDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('prepares the interview as it opens, copies the pitch and keeps it all as a note', () => {
    const fixture = create(() => ai.interview.and.returnValue(of(preparation)));
    const element: HTMLElement = fixture.nativeElement;
    expect(ai.interview).toHaveBeenCalledWith(9, 'FR');
    expect(element.querySelector('[data-ai-interview-pitch]')?.textContent).toContain('Bonjour, je suis Alice.');
    expect(element.textContent).toContain('Idempotence.');
    fixture.componentInstance.copyPitch();
    expect(notifications.success).toHaveBeenCalledWith('ai.interview.copied');

    projects.addProjectNote.and.returnValue(of({ id: 1 } as ProjectNote));
    fixture.componentInstance.keepAsNote();
    const note = projects.addProjectNote.calls.mostRecent().args[1];
    expect(projects.addProjectNote.calls.mostRecent().args[0]).toBe(9);
    expect(note).toContain('- **Kafka exactly-once ?** Idempotence.');
    expect(note).toContain('- **Un conflit ?**');
    expect(note).toContain("- Taille de l'équipe ?");
    expect(notifications.success).toHaveBeenCalledWith('ai.interview.kept');
  });

  it('says why it could not, and why a note was not kept', () => {
    const fixture = create(() => ai.interview.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } }))));
    expect(fixture.componentInstance.errorKey).toBe('ai.errors.unavailable');
    fixture.componentInstance.keepAsNote();
    expect(projects.addProjectNote).not.toHaveBeenCalled();
    ai.interview.and.returnValue(of({ ...preparation, toAsk: [] }));
    fixture.componentInstance.prepare();
    projects.addProjectNote.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.keepAsNote();
    expect(notifications.error).toHaveBeenCalledWith('ai.interview.keepError');
    expect(projects.addProjectNote.calls.mostRecent().args[1]).not.toContain('ai.interview.toAsk');
  });
});

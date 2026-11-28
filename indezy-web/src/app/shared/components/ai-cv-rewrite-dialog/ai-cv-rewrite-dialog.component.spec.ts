import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiCvRewrite } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AiCvRewriteDialogComponent } from './ai-cv-rewrite-dialog.component';

const REWRITE: AiCvRewrite = {
  headline: { before: 'Développeuse Java', after: 'Lead Java / Kafka' },
  summary: { before: 'Dix ans de Java.', after: 'Dix ans de Java.' },
  skillsBefore: ['Java', 'Kafka'],
  skills: ['Kafka', 'Java'],
  missions: [{ number: 1, id: null, title: 'Lead Java', before: 'Migration vers Kafka.', after: 'Migration du SI vers Kafka.' },
    { number: 2, id: null, title: 'Dev Java', before: null, after: null }],
  positions: [{ number: 1, id: 70, title: 'Développeuse', before: 'Paie en Java.', after: 'Logiciel de paie en Java.' }]
};

describe('AiCvRewriteDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<AiCvRewriteDialogComponent, boolean>>;

  function create(): { component: AiCvRewriteDialogComponent; element: HTMLElement } {
    ai = jasmine.createSpyObj('AiService', ['rewriteCv', 'rewrittenCv', 'approveCv']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [AiCvRewriteDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: AiService, useValue: ai }, { provide: NotificationService, useValue: notifications },
        { provide: MatDialogRef, useValue: dialogRef }]
    });
    const fixture = TestBed.createComponent(AiCvRewriteDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
  }

  it('asks for the positioning, style and language, then shows only the blocks the AI changed, kept', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.positioning = '  Lead Java ';
    component.style = 'TECHNICAL';
    component.language = 'EN';

    component.start();

    expect(ai.rewriteCv).toHaveBeenCalledWith('Lead Java', 'TECHNICAL', 'EN');
    expect(component.headline).toEqual({ keep: true, text: 'Lead Java / Kafka' });
    expect(component.summary.keep).toBeFalse();
    expect(component.keepSkills).toBeTrue();
    expect(component.missions.map(draft => draft.item.number)).toEqual([1]);
    expect(component.positions[0].text).toBe('Logiciel de paie en Java.');
  });

  it('says why the AI could not rewrite the CV', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.start();
    expect(component.rewriting).toBeFalse();
    expect(component.rewrite).toBeNull();
    expect(component.errorKey).toMatch(/^ai\.errors\./);
  });

  it('downloads the dossier with only the blocks kept, as edited', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    ai.rewrittenCv.and.returnValue(of(new Blob(['%PDF'])));
    component.headline.text = ' Staff Java ';
    component.keepSkills = false;
    component.positions[0].keep = false;

    component.download('ANONYMOUS');

    expect(ai.rewrittenCv).toHaveBeenCalledWith({ headline: 'Staff Java', summary: null, skills: [],
      missions: [{ number: 1, description: 'Migration du SI vers Kafka.' }], positions: [] }, 'ANONYMOUS', 'FR');
    expect(link.download).toBe('dossier-competences-anonymous.pdf');
    expect(component.downloading).toBeFalse();
  });

  it('writes the kept headline, summary and positions to the profile, then closes', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.approveCv.and.returnValue(of(undefined));
    component.summary = { keep: true, text: 'Experte Java. ' };

    component.approve();

    expect(ai.approveCv).toHaveBeenCalledWith({ headline: 'Lead Java / Kafka', summary: 'Experte Java.',
      positions: [{ id: 70, description: 'Logiciel de paie en Java.' }] });
    expect(notifications.success).toHaveBeenCalledWith('ai.cv.approved');
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('says when the profile could not be saved and stays open', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.approveCv.and.returnValue(throwError(() => new Error('down')));
    component.approve();
    expect(notifications.error).toHaveBeenCalledWith('ai.cv.approveError');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.approving).toBeFalse();
  });

  it('says why the reviewed PDF could not be made', async () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.rewrittenCv.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: new Blob(['{"message":"The AI declined this request"}']) })));
    component.download('NAMED');
    await new Promise(resolve => setTimeout(resolve, 20));
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.declined');
  });
});

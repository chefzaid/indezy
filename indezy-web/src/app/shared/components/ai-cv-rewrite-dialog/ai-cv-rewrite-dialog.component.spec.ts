import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatChipInputEvent } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { AiCvRewrite, CvProfile, ProfileCv } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileCvService } from '../../../services/profile-cv/profile-cv.service';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { AiCvRewriteDialogComponent, CvReviewData, ReviewBlock } from './ai-cv-rewrite-dialog.component';

const REWRITE: AiCvRewrite = {
  headline: { before: 'Développeuse Java', after: 'Lead Java / Kafka' },
  summary: { before: 'Dix ans de Java.', after: 'Dix ans de Java. ' },
  skillsBefore: ['Java', 'Kafka'],
  skills: ['Kafka', 'Java'],
  missions: [{ number: 1, id: null, title: 'Lead Java', before: 'Migration vers Kafka.', after: 'Migration du SI vers Kafka.', stack: 'Java, Kafka' },
    { number: 2, id: null, title: 'Dev Java', before: null, after: null }],
  positions: [{ number: 1, id: 70, title: 'Développeuse', before: 'Paie en Java.', after: 'Logiciel de paie en Java.' }]
};

describe('AiCvRewriteDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<AiCvRewriteDialogComponent, boolean>>;
  let cvProfiles: jasmine.SpyObj<CvProfileService> & { changed: Subject<void> };

  function create(data: CvReviewData | null = null, cv: ProfileCv | null = null): { component: AiCvRewriteDialogComponent; element: () => HTMLElement } {
    ai = jasmine.createSpyObj('AiService', ['rewriteCv', 'rewrittenCv', 'approveCv', 'refineCv', 'cvQuestions']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    cvProfiles = Object.assign(jasmine.createSpyObj<CvProfileService>('CvProfileService', ['create']), { changed: new Subject<void>() });
    TestBed.configureTestingModule({
      imports: [AiCvRewriteDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: AiService, useValue: ai }, { provide: NotificationService, useValue: notifications },
        { provide: MatDialogRef, useValue: dialogRef }, { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: ProfileCvService, useValue: { info: () => of(cv) } }, { provide: CvProfileService, useValue: cvProfiles }]
    });
    const fixture = TestBed.createComponent(AiCvRewriteDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: () => {
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    } };
  }

  function block(component: AiCvRewriteDialogComponent, key: string): ReviewBlock {
    return component.blocks.find(candidate => candidate.key === key)!;
  }

  function stubDownload(): HTMLAnchorElement {
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    ai.rewrittenCv.and.returnValue(of(new Blob(['%PDF'])));
    return link;
  }

  it('asks for the positioning, style and language, then shows only the blocks the AI changed, kept, their words compared', () => {
    const { component, element } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.positioning = '  Lead Java ';
    component.style = 'TECHNICAL';
    component.language = 'EN';

    component.start();

    expect(ai.rewriteCv).toHaveBeenCalledWith('Lead Java', 'TECHNICAL', 'EN', false);
    // The summary only gained a space: not shown.
    expect(component.blocks.map(candidate => candidate.key)).toEqual(['headline', 'mission-1', 'position-1']);
    expect(block(component, 'headline')).toEqual(jasmine.objectContaining({ keep: true, text: 'Lead Java / Kafka', refinable: false }));
    expect(component.keepSkills).toBeTrue();
    const diff = element().querySelector('[data-ai-cv-block="mission-1"] [data-ai-cv-diff]')!;
    expect(diff.querySelector('ins')?.textContent).toBe('du SI ');
    expect(diff.querySelector('del')).toBeNull();
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
    const link = stubDownload();
    block(component, 'headline').text = ' Staff Java ';
    component.keepSkills = false;
    block(component, 'position-1').keep = false;

    component.download('ANONYMOUS');

    expect(ai.rewrittenCv).toHaveBeenCalledWith({ headline: 'Staff Java', summary: null, skills: [],
      missions: [{ number: 1, description: 'Migration du SI vers Kafka.' }], positions: [] }, 'ANONYMOUS', 'FR', 'PDF');
    expect(link.download).toBe('dossier-competences-anonymous.pdf');
    expect(component.downloading).toBeFalse();
  });

  it('refines one block in one click, keeping the locked phrases, and never a locked block', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    component.addPhrase({ value: ' Kafka ', chipInput: { clear: jasmine.createSpy('clear') } } as unknown as MatChipInputEvent);
    component.addPhrase({ value: 'Kafka', chipInput: { clear: () => undefined } } as unknown as MatChipInputEvent);
    expect(component.lockedPhrases).toEqual(['Kafka']);
    component.focus = ' Kubernetes ';
    ai.refineCv.and.returnValue(of({ text: 'Migration vers Kafka.' }));
    const mission = block(component, 'mission-1');

    component.refine(mission, 'HIGHLIGHT');
    expect(ai.refineCv).toHaveBeenCalledWith({ text: 'Migration du SI vers Kafka.', refinement: 'HIGHLIGHT', focus: 'Kubernetes',
      locked: ['Kafka'], language: 'FR' });
    expect(mission.text).toBe('Migration vers Kafka.');
    expect(mission.refining).toBeFalse();

    mission.locked = true;
    component.refine(mission, 'SHORTER');
    expect(ai.refineCv).toHaveBeenCalledTimes(1);
    component.removePhrase('Kafka');
    expect(component.lockedPhrases).toEqual([]);
  });

  it('says when the AI changed a locked phrase, the block left as it was', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.refineCv.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: 'The AI changed a locked phrase: refine the block again or edit it by hand' } })));
    const position = block(component, 'position-1');
    component.refine(position, 'PLAIN');
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.locked');
    expect(position.text).toBe('Logiciel de paie en Java.');
  });

  it('writes the kept headline, summary and positions to the profile, then closes', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.approveCv.and.returnValue(of(undefined));

    component.approve();

    expect(ai.approveCv).toHaveBeenCalledWith({ headline: 'Lead Java / Kafka', summary: null,
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

  it('reviews a tailored dossier straight away, downloads it as tailored and hands the file on, never touching the profile', () => {
    const made = jasmine.createSpy('made');
    const { component, element } = create({ tailored: { rewrite: REWRITE, language: 'EN' }, made });
    expect(component.tailored).toBeTrue();
    expect(element().querySelector('[data-ai-cv-review]')).not.toBeNull();
    expect(element().querySelector('[data-ai-cv-approve]')).toBeNull();
    const link = stubDownload();

    component.download('NAMED');
    expect(ai.rewrittenCv.calls.mostRecent().args[2]).toBe('EN');
    expect(link.download).toBe('skills-profile-tailored.pdf');
    expect((made.calls.mostRecent().args[0] as File).name).toBe('skills-profile-tailored.pdf');
    // As an editable Word document, handed on as such.
    component.download('ANONYMOUS', 'DOCX');
    expect(ai.rewrittenCv.calls.mostRecent().args[3]).toBe('DOCX');
    expect(link.download).toBe('skills-profile-anonymous-tailored.docx');
    expect((made.calls.mostRecent().args[0] as File).type).toBe('application/vnd.openxmlformats-officedocument.wordprocessingml.document');
  });

  it('says why the reviewed PDF could not be made', async () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    ai.rewrittenCv.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: new Blob(['{"message":"The AI declined this request"}']) })));
    component.download('NAMED');
    // The error body is read from its blob first: wait for it rather than for a fixed time.
    for (let tries = 0; tries < 100 && !notifications.error.calls.count(); tries++) {
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.declined');
  });

  it('goes back to the positioning and style', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    component.again();
    expect(component.rewrite).toBeNull();
    expect(component.blocks).toEqual([]);
  });

  it('shows what a tailored dossier\'s target asks for, covered, only in the missions, or missing', () => {
    const { element } = create({ tailored: { rewrite: { ...REWRITE, coverage: { covered: ['Java'], partial: ['Kafka'], gaps: ['Rust'] } },
      language: 'FR' } });
    const coverage = element().querySelector('[data-ai-cv-coverage]')!;
    expect(coverage.querySelector('[data-ai-cv-covered]')?.textContent).toContain('Java');
    expect(coverage.querySelector('[data-ai-cv-partial]')?.textContent).toContain('Kafka');
    expect(coverage.querySelector('[data-ai-cv-gaps]')?.textContent).toContain('Rust');
  });

  it('checks each kept block for numbers and names the profile does not state, as it is edited', () => {
    const { component, element } = create({ tailored: { rewrite: REWRITE, language: 'FR' } });
    // "SI" is not in the profile; "Kafka" is.
    expect(component.unchecked.map(block => block.key)).toEqual(['mission-1']);
    expect(component.facts(block(component, 'mission-1'))).toEqual(['SI']);
    block(component, 'mission-1').text = 'Migration de 40 services vers Kafka.';
    expect(element().querySelector('[data-ai-cv-fact="mission-1"]')?.textContent).toContain('40');
    block(component, 'mission-1').keep = false;
    block(component, 'headline').text = 'Lead Java';
    expect(component.unchecked).toEqual([]);
    expect(element().querySelector('[data-ai-cv-fact]')).toBeNull();
  });

  it('asks what the vague blocks leave out and weaves the answer in', () => {
    const { component, element } = create({ tailored: { rewrite: REWRITE, language: 'EN' } });
    block(component, 'position-1').locked = true;
    ai.cvQuestions.and.returnValue(of([{ key: 'mission-1', question: 'How many services?' }, { key: 'gone', question: 'Lost?' }]));
    component.askQuestions();
    expect(ai.cvQuestions).toHaveBeenCalledWith([{ key: 'headline', text: 'Lead Java / Kafka' }, { key: 'mission-1', text: 'Migration du SI vers Kafka.' }],
      'EN');
    const mission = block(component, 'mission-1');
    expect(mission.question).toBe('How many services?');
    expect(element().querySelector('[data-ai-cv-question="mission-1"]')).not.toBeNull();

    ai.refineCv.and.returnValue(of({ text: 'Migration of 40 services to Kafka.' }));
    component.weave(mission);
    expect(ai.refineCv).not.toHaveBeenCalled();
    mission.answer = ' 40 services ';
    component.weave(mission);
    expect(ai.refineCv).toHaveBeenCalledWith({ text: 'Migration du SI vers Kafka.', refinement: 'WEAVE', focus: null, answer: '40 services',
      locked: [], language: 'EN' });
    expect(mission.text).toBe('Migration of 40 services to Kafka.');
    expect(mission.question).toBeUndefined();
  });

  it('says when no block needs a question, and why the AI could not ask', () => {
    const { component } = create({ tailored: { rewrite: REWRITE, language: 'FR' } });
    ai.cvQuestions.and.returnValue(of([]));
    component.askQuestions();
    expect(notifications.success).toHaveBeenCalledWith('ai.cv.noQuestions');
    ai.cvQuestions.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'The AI declined this request' } })));
    component.askQuestions();
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.declined');
    expect(component.asking).toBeFalse();
  });

  it('offers to bring in details from the kept CV file, and checks the facts against it too', () => {
    const { component, element } = create(null, { fileName: 'cv-alice.pdf', contentType: 'application/pdf', sizeBytes: 9, keptAt: '2026-11-28' });
    expect(element().querySelector('[data-ai-cv-from-file]')?.textContent).toContain('ai.cv.fromFile');
    component.fromFile = true;
    ai.rewriteCv.and.returnValue(of({ ...REWRITE, fileText: 'Migration du SI de paie vers Kafka.' }));
    component.start();
    expect(ai.rewriteCv).toHaveBeenCalledWith('', 'IMPACT', 'FR', true);
    // "SI" comes from the file: nothing new to check.
    expect(component.facts(block(component, 'mission-1'))).toEqual([]);
  });

  it('offers no file without one kept', () => {
    const { element } = create();
    expect(element().querySelector('[data-ai-cv-from-file]')).toBeNull();
  });

  it('saves the review as a CV profile, every mission and position in its order with the wording kept', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of({ ...REWRITE, missions: [{ ...REWRITE.missions[0], id: 10 }, { ...REWRITE.missions[1], id: 11 }] }));
    component.positioning = 'Lead Java';
    component.start();
    expect(component.profileName).toBe('Lead Java');
    block(component, 'position-1').keep = false;
    const changed = jasmine.createSpy('changed');
    cvProfiles.changed.subscribe(changed);
    cvProfiles.create.and.returnValue(of({ id: 1 } as CvProfile));

    component.saveProfile();

    expect(cvProfiles.create).toHaveBeenCalledWith({ name: 'Lead Java', language: 'FR', headline: 'Lead Java / Kafka', summary: null,
      skills: ['Kafka', 'Java'], missions: [{ id: 10, text: 'Migration du SI vers Kafka.' }, { id: 11, text: null }], positions: null });
    expect(notifications.success).toHaveBeenCalledWith('ai.cv.profileSaved', undefined, { name: 'Lead Java' });
    expect(changed).toHaveBeenCalled();
  });

  it('says when the CV profile could not be saved', () => {
    const { component } = create();
    ai.rewriteCv.and.returnValue(of(REWRITE));
    component.start();
    component.profileName = 'Backend';
    cvProfiles.create.and.returnValue(throwError(() => new Error('full')));
    component.saveProfile();
    expect(notifications.error).toHaveBeenCalledWith('cvProfiles.saveError');
    expect(component.savingProfile).toBeFalse();
  });
});

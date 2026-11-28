import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { Router, provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ProjectAttachment, ProjectDto, ProjectNote } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { ProjectService } from '../../../services/project/project.service';
import { ProjectAttachmentService } from '../../../services/project/project-attachment.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { OfferPackComponent } from './offer-pack.component';

describe('OfferPackComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let offers: jasmine.SpyObj<OfferService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let attachments: jasmine.SpyObj<ProjectAttachmentService>;
  const pack = { subject: 'Candidature Lead Kotlin', message: 'Bonjour,', screening: [{ question: 'Disponibilité', answer: 'Dès janvier.' }],
    rate: 620, rateReasons: ['Kotlin rare'] };

  function create() {
    ai = jasmine.createSpyObj('AiService', ['applicationPack', 'offerDossier']);
    attachments = jasmine.createSpyObj('ProjectAttachmentService', ['upload']);
    offers = jasmine.createSpyObj('OfferService', ['importOffer']);
    projects = jasmine.createSpyObj('ProjectService', ['addProjectNote']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    const clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [OfferPackComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: OfferService, useValue: offers }, { provide: ProjectService, useValue: projects },
        { provide: NotificationService, useValue: notifications }, { provide: Clipboard, useValue: clipboard },
        { provide: ProjectAttachmentService, useValue: attachments }, { provide: UserManagementService, useValue: {} }]
    });
    const fixture = TestBed.createComponent(OfferPackComponent);
    fixture.componentRef.setInput('offerId', 5);
    fixture.detectChanges();
    return fixture;
  }

  it('prepares the pack for the channel chosen, copies it and adds the offer with the pack in its journal', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    ai.applicationPack.and.returnValue(of(pack));
    component.channel.setValue('MALT');
    component.prepare();
    expect(ai.applicationPack).toHaveBeenCalledWith(5, 'MALT', 'FR');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dès janvier.');
    component.message.setValue('Bonjour, je suis disponible.');
    component.copy();
    expect(notifications.success).toHaveBeenCalledWith('ai.pack.copied');

    offers.importOffer.and.returnValue(of({ id: 42 } as ProjectDto));
    projects.addProjectNote.and.returnValue(of({ id: 1 } as ProjectNote));
    component.addToPipeline();
    expect(offers.importOffer).toHaveBeenCalledWith(5);
    const note = projects.addProjectNote.calls.mostRecent().args[1];
    expect(note).toContain('Bonjour, je suis disponible.');
    expect(note).toContain('- **Disponibilité** Dès janvier.');
    expect(navigate).toHaveBeenCalledWith(['/projects', 42]);
  });

  it('says why the pack could not be prepared or the offer added', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.addToPipeline();
    expect(offers.importOffer).not.toHaveBeenCalled();
    ai.applicationPack.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    component.prepare();
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.unavailable');
    ai.applicationPack.and.returnValue(of({ ...pack, subject: '', screening: [], rate: 0 }));
    component.prepare();
    offers.importOffer.and.returnValue(throwError(() => new Error('down')));
    component.addToPipeline();
    expect(notifications.error).toHaveBeenCalledWith('ai.pack.addError');
    expect(component.adding).toBeFalse();
  });

  it('attaches the dossier tailored to the offer to the opportunity it becomes', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    ai.applicationPack.and.returnValue(of(pack));
    component.prepare();
    const dossier = new File(['%PDF'], 'dossier-competences-tailored.pdf', { type: 'application/pdf' });
    component.dossier = dossier;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-offer-pack-dossier]')).not.toBeNull();
    offers.importOffer.and.returnValue(of({ id: 42 } as ProjectDto));
    projects.addProjectNote.and.returnValue(of({ id: 1 } as ProjectNote));
    attachments.upload.and.returnValue(of({ id: 3 } as ProjectAttachment));

    component.addToPipeline();
    expect(attachments.upload).toHaveBeenCalledWith(42, dossier);
    expect(notifications.success).toHaveBeenCalledWith('ai.pack.added');

    // The opportunity is added even when the dossier could not be attached; it says so.
    attachments.upload.and.returnValue(throwError(() => new Error('too large')));
    component.addToPipeline();
    expect(notifications.error).toHaveBeenCalledWith('ai.pack.attachError');
  });
});

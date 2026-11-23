import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NetworkDraft } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NetworkService } from '../../../services/network/network.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AiAvailabilityDialogComponent } from './ai-availability-dialog.component';

describe('AiAvailabilityDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let clipboard: jasmine.SpyObj<Clipboard>;

  function create(): AiAvailabilityDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['availability']);
    network = jasmine.createSpyObj('NetworkService', ['createDraft', 'missions']);
    network.missions.and.returnValue(of({ posts: [], page: 0, more: false }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [AiAvailabilityDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: Clipboard, useValue: clipboard }, { provide: ContactService, useValue: { availabilityRecipients: () => of([]), scheduledEmails: () => of([]), campaigns: () => of([]) } }]
    });
    const fixture = TestBed.createComponent(AiAvailabilityDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('drafts both posts, copies them and keeps the network one as a draft', () => {
    const component = create();
    ai.availability.and.returnValue(of({ linkedin: 'Disponible en février', network: 'Dispo le 1er février', availableFrom: '2027-02-01' }));
    component.language.setValue('EN');
    component.write();
    expect(ai.availability).toHaveBeenCalledWith('EN');
    expect(component.linkedin.value).toBe('Disponible en février');
    expect(component.availableFrom).toBe('2027-02-01');
    component.copy(component.linkedin.value);
    expect(clipboard.copy).toHaveBeenCalledWith('Disponible en février');
    expect(notifications.success).toHaveBeenCalledWith('ai.availability.copied');

    network.createDraft.and.returnValue(of({ id: 1, text: 'Dispo le 1er février' } as unknown as NetworkDraft));
    component.keepDraft();
    expect(network.createDraft).toHaveBeenCalledWith('Dispo le 1er février', null);
    expect(notifications.success).toHaveBeenCalledWith('ai.availability.drafted');
  });

  it('says why nothing could be written or kept', () => {
    const component = create();
    ai.availability.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    component.write();
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.unavailable');
    expect(component.writing).toBeFalse();

    component.keepDraft();
    expect(network.createDraft).not.toHaveBeenCalled();
    component.network.setValue('Dispo');
    network.createDraft.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    component.keepDraft();
    expect(notifications.error).toHaveBeenCalledWith('ai.availability.draftError');
    expect(component.keeping).toBeFalse();
  });
});

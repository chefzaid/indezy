import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { CvProfile, CvShare } from '../../../models';
import { CvShareService } from '../../../services/cv-profile/cv-share.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { CvShareDialogComponent } from './cv-share-dialog.component';

const PROFILE = { id: 3, name: 'Backend' } as CvProfile;
const SHARE: CvShare = { id: 5, profileId: 3, profileName: 'Backend', recipient: 'ESN Alpha', layout: 'NAMED', createdAt: '2026-11-28T08:00:00',
  expiresAt: '2026-12-05T08:00:00Z', revokedAt: null, active: true, openCount: 2, downloadCount: 1, lastOpenedAt: null };

describe('CvShareDialogComponent', () => {
  let service: jasmine.SpyObj<CvShareService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let clipboard: jasmine.SpyObj<Clipboard>;

  function create(): { component: CvShareDialogComponent; element: HTMLElement; detect: () => void } {
    service = jasmine.createSpyObj('CvShareService', ['list', 'create', 'revoke']);
    service.list.and.returnValue(of([SHARE, { ...SHARE, id: 6, profileId: 4 }]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [CvShareDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: CvShareService, useValue: service }, { provide: NotificationService, useValue: notifications },
        { provide: Clipboard, useValue: clipboard }, { provide: MAT_DIALOG_DATA, useValue: { profile: PROFILE } }]
    });
    const fixture = TestBed.createComponent(CvShareDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement, detect: () => fixture.detectChanges() };
  }

  it('lists only this profile\'s links with their openings', () => {
    const { component, element } = create();
    expect(component.shares.map(share => share.id)).toEqual([5]);
    expect(element.querySelector('[data-cv-share="5"]')?.textContent).toContain('ESN Alpha');
  });

  it('makes a link for the recipient chosen and shows it once, to copy', () => {
    const { component, element, detect } = create();
    service.create.and.returnValue(of({ share: SHARE, token: 'secret-token' }));
    component.recipient = ' ESN Alpha ';
    component.layout = 'ANONYMOUS';
    component.days = 14;
    component.share();
    expect(service.create).toHaveBeenCalledWith(3, 'ESN Alpha', 'ANONYMOUS', 14);
    expect(component.link).toEqual({ recipient: 'ESN Alpha', url: `${location.origin}/shared-cv#secret-token` });
    detect();
    expect((element.querySelector('[data-cv-share-url]') as HTMLInputElement).value).toContain('#secret-token');
    component.copy();
    expect(clipboard.copy).toHaveBeenCalledWith(`${location.origin}/shared-cv#secret-token`);
    expect(notifications.success).toHaveBeenCalledWith('cvShares.copied');
  });

  it('revokes a link, and says when one cannot be made', () => {
    const { component } = create();
    service.revoke.and.returnValue(of({ ...SHARE, active: false }));
    component.revoke(SHARE);
    expect(service.revoke).toHaveBeenCalledWith(5);
    service.create.and.returnValue(throwError(() => new Error('full')));
    component.recipient = 'ESN';
    component.share();
    expect(notifications.error).toHaveBeenCalledWith('cvShares.createError');
    expect(component.sharing).toBeFalse();
  });

  it('says which names keep an anonymized CV from being shared', () => {
    const { component, element, detect } = create();
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: 'The anonymized CV still names Alice Corp, Julie Durand: change its wording before sharing it' } })));
    component.recipient = 'ESN';
    component.layout = 'ANONYMOUS';
    component.share();
    expect(component.leak).toBe('Alice Corp, Julie Durand');
    expect(notifications.error).not.toHaveBeenCalled();
    detect();
    expect(element.querySelector('[data-cv-share-leak]')).not.toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { InboxWatcher } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { InboxWatcherComponent } from './inbox-watcher.component';

describe('InboxWatcherComponent', () => {
  const address = 'inbox+' + 'a'.repeat(40) + '@replies.example';
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(watcher: InboxWatcher) {
    ai = jasmine.createSpyObj<AiService>('AiService', ['inboxWatcher', 'renewInboxAddress']);
    ai.inboxWatcher.and.returnValue(of(watcher));
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [InboxWatcherComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AiService, useValue: ai }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(InboxWatcherComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('stays away while Indezy cannot receive emails', () => {
    const fixture = create({ available: false, address: null, recent: [] });
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-inbox-watcher]')).toBeNull();
  });

  it('gives an address on request, copies it, and tells when it fails', () => {
    const fixture = create({ available: true, address: null, recent: [] });
    const element = fixture.nativeElement as HTMLElement;
    ai.renewInboxAddress.and.returnValue(throwError(() => new Error('down')));
    (element.querySelector('[data-inbox-create]') as HTMLButtonElement).click();
    expect(notifications.error).toHaveBeenCalledWith('inboxWatcher.failed');

    ai.renewInboxAddress.and.returnValue(of({ available: true, address, recent: [] }));
    (element.querySelector('[data-inbox-create]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-inbox-address]')!.textContent).toContain(address);
    const copy = spyOn(TestBed.inject(Clipboard), 'copy').and.returnValue(true);
    (element.querySelector('[data-inbox-copy]') as HTMLButtonElement).click();
    expect(copy).toHaveBeenCalledWith(address);
    expect(notifications.success).toHaveBeenCalledWith('inboxWatcher.copied');
  });

  it('lists what was done with the latest forwarded emails, linking the opportunity', () => {
    const fixture = create({ available: true, address, recent: [
      { id: 1, sender: 'ines@acme.example', subject: 'Entretien', kind: 'INTERVIEW_INVITE', action: 'INTERVIEW_STEP', contactId: 3,
        projectId: 42, receivedAt: '2026-11-24T10:00:00' },
      { id: 2, sender: 'news@acme.example', subject: null, kind: 'OTHER', action: 'NONE', contactId: null, projectId: null,
        receivedAt: '2026-11-24T09:00:00' }] });
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-inbox-renew]')).not.toBeNull();
    const invite = element.querySelector('[data-inbox-email="INTERVIEW_INVITE"]')!;
    expect(invite.textContent).toContain('inboxWatcher.actions.INTERVIEW_STEP');
    expect(invite.querySelector('[data-inbox-project="42"]')).not.toBeNull();
    expect(element.querySelector('[data-inbox-email="OTHER"]')!.textContent).toContain('news@acme.example');
  });
});

import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkDraft, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NetworkDraftsComponent } from './network-drafts.component';
import { stubNetworkNav } from '../nav/network-nav.stub';

describe('NetworkDraftsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const drafts: NetworkDraft[] = [{ id: 2, text: 'Lundi', scheduledAt: '2026-11-16T08:00:00Z', updatedAt: '2026-11-14T10:00:00' },
    { id: 1, text: 'À relire', scheduledAt: null, updatedAt: '2026-11-14T09:00:00' }];

  function create(answer = of(drafts)) {
    network = jasmine.createSpyObj('NetworkService', ['drafts', 'publishDraft', 'deleteDraft']);
    network.drafts.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [NetworkDraftsComponent, TranslateModule.forRoot()],
      providers: [{ provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    stubNetworkNav(NetworkDraftsComponent);
    const fixture = TestBed.createComponent(NetworkDraftsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the drafts, publishes one now and deletes one once confirmed', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-draft="2"] .when')?.textContent).toContain('network.drafts.scheduledFor');
    expect(element.querySelector('[data-draft="1"] .when')?.textContent).toContain('network.drafts.kept');
    network.publishDraft.and.returnValue(of({} as NetworkPost));
    (element.querySelector('[data-draft="2"] [data-draft-publish]') as HTMLButtonElement).click();
    expect(network.publishDraft).toHaveBeenCalledWith(2);
    expect(component.drafts.map(draft => draft.id)).toEqual([1]);
    network.publishDraft.and.returnValue(of({ held: true }));
    component.publish(drafts[1]);
    expect(notifications.success).toHaveBeenCalledWith('network.feed.held');
    expect(component.drafts).toEqual([]);
    network.publishDraft.and.returnValue(throwError(() => new Error('down')));
    component.publish(drafts[1]);
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');
    confirm.confirm.and.returnValue(of(false));
    component.remove(drafts[1]);
    expect(network.deleteDraft).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.deleteDraft.and.returnValue(of(undefined));
    component.remove(drafts[1]);
    fixture.detectChanges();
    expect(element.querySelector('[data-drafts-empty]')).not.toBeNull();
    network.deleteDraft.and.returnValue(throwError(() => new Error('down')));
    component.remove(drafts[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.drafts.saveError');
  });

  it('says when the drafts cannot be loaded', () => {
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('network.drafts.loadError');
  });
});

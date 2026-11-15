import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NotificationGroup } from '../../../models';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkNotificationsComponent } from './network-notifications.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkNotificationsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notices: jasmine.SpyObj<NetworkNoticesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const member = (handle: string, firstName: string): MemberCard => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const group = (type: NotificationGroup['type'], count: number, read = false): NotificationGroup => ({ type, count, read,
    postId: type === 'FOLLOW' ? null : 4, postExcerpt: type === 'FOLLOW' ? null : 'Une question', latestAt: '2026-11-14T10:00:00',
    actors: [member('leo_m', 'Léo'), member('ines_b', 'Inès'), member('paul_d', 'Paul')].slice(0, Math.min(count, 3)) });

  function create(groups = of([group('LIKE', 4), group('FOLLOW', 1), group('COMMENT', 2, true)])): HTMLElement {
    network = jasmine.createSpyObj('NetworkService', ['notifications', 'readNotifications']);
    network.notifications.and.returnValue(groups);
    network.readNotifications.and.returnValue(of(undefined));
    notices = jasmine.createSpyObj('NetworkNoticesService', ['refresh']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [NetworkNotificationsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkService, useValue: network }, { provide: NetworkNoticesService, useValue: notices },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkNotificationsComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('tells who did what, the unread marked, then marks them read and counts again', () => {
    const element = create();
    const text = (type: string) => element.querySelector(`[data-notice="${type}"] p`)?.textContent?.replace(/\s+/g, ' ').trim();
    expect(text('LIKE')).toContain('Léo and 3 others liked your post');
    expect(text('FOLLOW')).toContain('Léo started following you');
    expect(text('COMMENT')).toContain('Léo and Inès commented your post');
    expect(element.querySelector('[data-notice="LIKE"] .excerpt')?.textContent).toContain('Une question');
    expect(element.querySelector('[data-notice="LIKE"]')?.classList).toContain('unread');
    expect(network.readNotifications).toHaveBeenCalled();
    expect(notices.refresh).toHaveBeenCalled();
  });

  it('tells the moderators\' word without a member', () => {
    const element = create(of([{ ...group('SUSPENDED', 1), actors: [], postId: null, postExcerpt: null },
      { ...group('REPORT_ACTED', 2, true), actors: [], postId: null, postExcerpt: null }]));
    expect(element.querySelector('[data-notice="SUSPENDED"] p')?.textContent).toContain('The moderators suspended you');
    expect(element.querySelector('[data-notice="SUSPENDED"] a')).toBeNull();
    expect(element.querySelector('[data-notice="REPORT_ACTED"] p')?.textContent).toContain('acted on a report you made');
    expect(element.querySelector('[data-notice="REPORT_ACTED"] mat-icon')?.textContent).toBe('gavel');
  });

  it('marks nothing read when all is read, and says when there is nothing or it cannot be loaded', () => {
    let element = create(of([group('REPOST', 1, true)]));
    expect(network.readNotifications).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    element = create(of([]));
    expect(element.querySelector('[data-notices-empty]')).not.toBeNull();
    TestBed.resetTestingModule();
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('network.notices.loadError');
  });
});

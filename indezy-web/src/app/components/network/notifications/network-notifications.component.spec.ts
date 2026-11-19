import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NotificationGroup } from '../../../models';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkNotificationsComponent } from './network-notifications.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('NetworkNotificationsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notices: jasmine.SpyObj<NetworkNoticesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let fixture: ComponentFixture<NetworkNotificationsComponent>;
  const detect = (_element: HTMLElement) => fixture.detectChanges();
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
    stubNetworkNav(NetworkNotificationsComponent);
    stubNetworkColumns(NetworkNotificationsComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(NetworkNotificationsComponent);
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

  it('filters the notifications by kind from the menu or the chips, with how many each holds, and opens their settings', () => {
    const element = create(of([group('LIKE', 4), group('MENTION', 1), group('FOLLOW', 1, true), group('REPLY', 1, true)]));
    const shown = () => Array.from(element.querySelectorAll('[data-notice]')).map(item => item.getAttribute('data-notice'));
    expect(shown()).toEqual(['LIKE', 'MENTION', 'FOLLOW', 'REPLY']);
    expect(element.querySelector('[data-notice-filter="unread"] .n')?.textContent?.trim()).toBe('2');
    expect(element.querySelector('[data-notice-filter="moderation"] .n')).toBeNull();
    (element.querySelector('[data-notice-filter="comments"]') as HTMLButtonElement).click();
    detect(element);
    expect(shown()).toEqual(['REPLY']);
    expect(element.querySelector('[data-notice-filter="comments"]')?.getAttribute('aria-pressed')).toBe('true');
    (element.querySelector('[data-notice-chip="unread"]') as HTMLButtonElement).click();
    detect(element);
    expect(shown()).toEqual(['LIKE', 'MENTION']);
    (element.querySelector('[data-notice-filter="moderation"]') as HTMLButtonElement).click();
    detect(element);
    expect(element.querySelector('[data-notices-none-filtered]')).not.toBeNull();
    (element.querySelector('[data-notices-none-filtered] button') as HTMLButtonElement).click();
    detect(element);
    expect(shown().length).toBe(4);
    expect(element.querySelector('[data-notice-settings]')?.getAttribute('href')).toBe('/profile?tab=network');
  });

  it('groups by period and leads to the post, the member, or nowhere for the moderators', () => {
    const element = create();
    const now = new Date('2026-11-15T12:00:00');
    const at = (latestAt: string) => ({ ...group('LIKE', 1), latestAt });
    const sections = NetworkNotificationsComponent.byPeriod([at('2026-11-15T08:00:00'), at('2026-11-12T08:00:00'), at('2026-10-01T08:00:00')],
      now);
    expect(sections.map(section => section.key)).toEqual(['today', 'week', 'earlier']);
    expect(NetworkNotificationsComponent.byPeriod([at('2026-11-15T08:00:00')], now).map(section => section.key)).toEqual(['today']);
    expect(element.querySelector('[data-notice="LIKE"] [data-notice-link]')?.getAttribute('href')).toBe('/network/posts/4');
    expect(element.querySelector('[data-notice="FOLLOW"] [data-notice-link]')?.getAttribute('href')).toBe('/m/leo_m');
  });
});

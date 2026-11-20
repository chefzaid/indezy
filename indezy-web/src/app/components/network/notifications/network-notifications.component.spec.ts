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
    network = jasmine.createSpyObj('NetworkService', ['notifications', 'readNotifications', 'dismissNotification', 'notificationSettings',
      'saveNotificationSettings']);
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
    expect(element.querySelector('[data-notice="REPORT_ACTED"] mat-icon.kind')?.textContent).toBe('gavel');
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

  it('marks all read, deletes a notification, and turns its kind off, saying when it cannot', () => {
    const element = create(of([group('LIKE', 4), group('FOLLOW', 1)]));
    const component = fixture.componentInstance;
    notices.refresh.calls.reset();
    expect(element.querySelector('[data-notices-mark-all]')).not.toBeNull();
    (element.querySelector('[data-notices-mark-all]') as HTMLButtonElement).click();
    detect(element);
    expect(element.querySelector('li.unread')).toBeNull();
    expect(element.querySelector('[data-notices-mark-all]')).toBeNull();
    expect(notices.refresh).toHaveBeenCalled();

    network.dismissNotification.and.returnValue(of(undefined));
    component.dismiss(component.groups[0]);
    detect(element);
    expect(network.dismissNotification).toHaveBeenCalledWith('LIKE', 4);
    expect(element.querySelector('[data-notice="LIKE"]')).toBeNull();
    network.dismissNotification.and.returnValue(throwError(() => new Error('down')));
    component.dismiss(component.groups[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.notices.actionError');

    network.notificationSettings.and.returnValue(of({ off: ['REPOST'], emailed: ['FOLLOW', 'LIKE'] }));
    network.saveNotificationSettings.and.returnValue(of({ off: ['REPOST', 'FOLLOW'], emailed: ['LIKE'] }));
    component.turnOff(component.groups[0]);
    expect(network.saveNotificationSettings).toHaveBeenCalledWith(['REPOST', 'FOLLOW'], ['LIKE']);
    expect(notifications.success).toHaveBeenCalledWith('network.notices.turnedOff');
    network.readNotifications.and.returnValue(throwError(() => new Error('down')));
    component.markAllRead();
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });

  it('names the community a notification is about and leads to it, or to the post posted in it', () => {
    const element = create(of([{ ...group('COMMUNITY_REQUEST', 1), postId: null, postExcerpt: null, community: { slug: 'kafka', name: 'Kafka' } },
      { ...group('COMMUNITY_POST', 2), community: { slug: 'kafka', name: 'Kafka' } }]));
    expect(element.querySelector('[data-notice="COMMUNITY_REQUEST"] [data-notice-community]')?.textContent).toContain('Kafka');
    expect(element.querySelector('[data-notice="COMMUNITY_REQUEST"] [data-notice-link]')?.getAttribute('href')).toBe('/network/communities/kafka');
    expect(element.querySelector('[data-notice="COMMUNITY_POST"] [data-notice-link]')?.getAttribute('href')).toBe('/network/posts/4');
    expect(element.querySelector('[data-notice="COMMUNITY_POST"] p')?.textContent).toContain('posted in one of your communities');
    expect(element.querySelector('[data-notice-filter="communities"] .n')?.textContent?.trim()).toBe('2');
  });

  it('tells an idea the reader supports moved on, from Indezy, and leads to it', () => {
    const element = create(of([{ ...group('IDEA_STATUS', 1), postId: null, postExcerpt: null, actors: [],
      idea: { id: 12, title: 'Thème sombre', status: 'PLANNED' } }]));
    const notice = element.querySelector('[data-notice="IDEA_STATUS"]') as HTMLElement;
    expect(notice.querySelector('[data-notice-idea]')?.textContent).toContain('An idea you support is now: Planned');
    expect(notice.textContent).toContain('« Thème sombre »');
    expect(notice.querySelector('app-member-avatar')).toBeNull();
    expect(notice.querySelector('[data-notice-link]')?.getAttribute('href')).toBe('/network/ideas/12');
  });

  it('reminds of an event the reader answered and leads to it', () => {
    const element = create(of([{ ...group('MEETUP_REMINDER', 1), postId: null, postExcerpt: null, actors: [],
      meetup: { id: 6, title: 'Apéro Kafka', startsAt: '2026-12-03T18:30:00' } }]));
    const notice = element.querySelector('[data-notice="MEETUP_REMINDER"]') as HTMLElement;
    expect(notice.querySelector('[data-notice-meetup]')?.textContent).toContain('Tomorrow: an event you answered');
    expect(notice.textContent).toContain('« Apéro Kafka »');
    expect(notice.querySelector('[data-notice-link]')?.getAttribute('href')).toBe('/network/events/6');
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

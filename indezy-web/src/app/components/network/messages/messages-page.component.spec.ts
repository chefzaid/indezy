import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { Conversation } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { CONVERSATIONS_REFRESH_MS, MessagesPageComponent, SEARCH_DELAY_MS } from './messages-page.component';
import en from '../../../../assets/i18n/en.json';

describe('MessagesPageComponent', () => {
  let api: jasmine.SpyObj<NetworkMessagesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let fixture: ComponentFixture<MessagesPageComponent>;
  const conversation = (id: number, changes: Partial<Conversation> = {}): Conversation => ({ id, with: { handle: `member${id}`,
    firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false, availability: null,
    availabilityDate: null, languages: [], verified: false }, last: { mine: id === 2, text: `Bonjour ${id}`, deleted: false,
    sentAt: '2026-11-16T09:00:00' }, unread: id === 1 ? 2 : 0, muted: id === 2, archived: false, closed: null,
    lastMessageAt: `2026-11-16T0${9 - id}:00:00`, ...changes });

  function create(list = of([conversation(1), conversation(2)]), id: string | null = null): HTMLElement {
    api = jasmine.createSpyObj('NetworkMessagesService', ['conversations', 'thread', 'read', 'search']);
    api.conversations.and.returnValue(list);
    api.thread.and.returnValue(of({ conversation: conversation(1), messages: [], more: false }));
    api.read.and.returnValue(of(conversation(1, { unread: 0 })));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    params = new BehaviorSubject(convertToParamMap(id ? { id } : {}));
    TestBed.configureTestingModule({
      imports: [MessagesPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: params } }, { provide: NetworkMessagesService, useValue: api },
        { provide: NetworkNoticesService, useValue: jasmine.createSpyObj('NetworkNoticesService', ['refreshMessages']) },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(MessagesPageComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('lists the conversations with their latest message and unread count, and opens one beside them', fakeAsync(() => {
    const element = create();
    expect(element.querySelectorAll('[data-conversation]').length).toBe(2);
    expect(element.querySelector('[data-conversation="1"] [data-conversation-unread]')?.textContent).toContain('2');
    expect(element.querySelector('[data-conversation="2"]')?.textContent).toContain('You: Bonjour 2');
    expect(element.querySelector('[data-conversation="1"]')?.getAttribute('href')).toBe('/network/messages/1');
    expect(element.querySelector('[data-messages-pick]')).not.toBeNull();
    params.next(convertToParamMap({ id: '1' }));
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(element.querySelector('app-message-thread')).not.toBeNull();
    expect(element.querySelector('[data-conversation="1"]')?.getAttribute('aria-current')).toBe('page');
    expect(element.querySelector('[data-messages-page]')?.classList).toContain('open');
    discardPeriodicTasks();
  }));

  it('follows the open conversation, reads the list again every half minute, and shows the archived ones', fakeAsync(() => {
    create();
    const component = fixture.componentInstance;
    component.updated(conversation(1, { unread: 0 }));
    expect(component.conversations.map(item => item.unread)).toEqual([0, 0]);
    component.updated(conversation(2, { lastMessageAt: '2026-11-16T10:00:00' }));
    expect(component.conversations.map(item => item.id)).toEqual([2, 1]);
    component.updated(conversation(1, { archived: true }));
    expect(component.conversations.map(item => item.id)).toEqual([2]);
    component.updated(conversation(3));
    expect(component.conversations.map(item => item.id)).toEqual([3, 2]);

    tick(CONVERSATIONS_REFRESH_MS);
    expect(api.conversations).toHaveBeenCalledTimes(2);
    api.conversations.and.returnValue(of([]));
    component.showArchived(true);
    component.showArchived(true);
    fixture.detectChanges();
    expect(api.conversations).toHaveBeenCalledWith(true);
    expect(api.conversations).toHaveBeenCalledTimes(3);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-messages-empty]')?.textContent).toContain('No archived conversation.');
    discardPeriodicTasks();
  }));

  it('searches the messages once two characters are typed, each found opening its conversation', fakeAsync(() => {
    const element = create();
    const component = fixture.componentInstance;
    api.search.and.returnValue(of([{ conversationId: 1, with: conversation(1).with, messageId: 9, mine: true, excerpt: 'Une mission Kafka',
      sentAt: '2026-11-16T09:00:00' }]));
    component.query.setValue('k');
    tick(SEARCH_DELAY_MS);
    expect(api.search).not.toHaveBeenCalled();
    expect(component.found).toBeNull();
    component.query.setValue(' kafka ');
    tick(SEARCH_DELAY_MS);
    fixture.detectChanges();
    expect(api.search).toHaveBeenCalledWith('kafka');
    const hit = element.querySelector('[data-found-message="9"]')!;
    expect(hit.getAttribute('href')).toBe('/network/messages/1');
    expect(hit.textContent).toContain('You: Une mission Kafka');
    expect(element.querySelector('[data-conversation]')).toBeNull();
    api.search.and.returnValue(throwError(() => new Error('down')));
    component.query.setValue('java');
    tick(SEARCH_DELAY_MS);
    fixture.detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.loadError');
    expect(element.querySelector('[data-messages-found]')?.textContent).toContain('No message holds these words.');
    component.query.setValue('');
    tick(SEARCH_DELAY_MS);
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-conversation]').length).toBe(2);
    discardPeriodicTasks();
  }));

  it('asks a visitor to join, and says when the conversations cannot be loaded', fakeAsync(() => {
    let element = create(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect(element.querySelector('[data-messages-not-member]')).not.toBeNull();
    TestBed.resetTestingModule();
    element = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(notifications.error).toHaveBeenCalledWith('network.messages.loadError');
    discardPeriodicTasks();
  }));
});

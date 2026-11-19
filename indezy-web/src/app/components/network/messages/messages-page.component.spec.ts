import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';

import { Conversation } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { CONVERSATIONS_REFRESH_MS, MessagesPageComponent, SEARCH_DELAY_MS, STREAM_SETTLE_MS } from './messages-page.component';
import { NetworkEvent, NetworkStreamService } from '../../../services/network/network-stream.service';
import { GroupDialogComponent } from './group-dialog.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';

describe('MessagesPageComponent', () => {
  let api: jasmine.SpyObj<NetworkMessagesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let fixture: ComponentFixture<MessagesPageComponent>;
  let events: Subject<NetworkEvent>;
  let stream: { events: Subject<NetworkEvent>; connected: boolean };
  const conversation = (id: number, changes: Partial<Conversation> = {}): Conversation => ({ id, with: { handle: `member${id}`,
    firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false, availability: null,
    availabilityDate: null, languages: [], verified: false }, group: null, last: { mine: id === 2, text: `Bonjour ${id}`, deleted: false,
    sentAt: '2026-11-16T09:00:00' }, unread: id === 1 ? 2 : 0, muted: id === 2, archived: false, closed: null,
    lastMessageAt: `2026-11-16T0${9 - id}:00:00`, ...changes });

  function create(list = of([conversation(1), conversation(2)]), id: string | null = null): HTMLElement {
    api = jasmine.createSpyObj('NetworkMessagesService', ['conversations', 'thread', 'read', 'search', 'createGroup', 'open']);
    api.conversations.and.returnValue(list);
    api.thread.and.returnValue(of({ conversation: conversation(1), messages: [], more: false }));
    api.read.and.returnValue(of(conversation(1, { unread: 0 })));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    params = new BehaviorSubject(convertToParamMap(id ? { id } : {}));
    events = new Subject<NetworkEvent>();
    stream = { events, connected: false };
    TestBed.configureTestingModule({
      imports: [MessagesPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: params } }, { provide: NetworkMessagesService, useValue: api },
        { provide: NetworkNoticesService, useValue: jasmine.createSpyObj('NetworkNoticesService', ['refreshMessages']) },
        { provide: NotificationService, useValue: notifications }, { provide: NetworkStreamService, useValue: stream }]
    });
    stubNetworkNav(MessagesPageComponent);
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
    // While the stream is open, the list waits for its events, read once a burst of them ended.
    stream.connected = true;
    tick(CONVERSATIONS_REFRESH_MS);
    expect(api.conversations).toHaveBeenCalledTimes(2);
    events.next({ type: 'message', conversationId: 1 });
    events.next({ type: 'read', conversationId: 1 });
    events.next({ type: 'typing', conversationId: 1, name: 'Léo' });
    tick(STREAM_SETTLE_MS);
    expect(api.conversations).toHaveBeenCalledTimes(3);
    stream.connected = false;
    api.conversations.calls.reset();
    api.conversations.and.returnValue(of([]));
    component.showArchived(true);
    component.showArchived(true);
    fixture.detectChanges();
    expect(api.conversations).toHaveBeenCalledWith(true);
    expect(api.conversations).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-messages-empty]')?.textContent).toContain('No archived conversation.');
    discardPeriodicTasks();
  }));

  it('searches the messages once two characters are typed, each found opening its conversation', fakeAsync(() => {
    const element = create();
    const component = fixture.componentInstance;
    api.search.and.returnValue(of([{ conversationId: 1, title: 'Léo Martin', with: conversation(1).with, messageId: 9, mine: true, excerpt: 'Une mission Kafka',
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

  it('creates a group among the members the reader can write to and opens it, shows groups by their name, and leaves one', fakeAsync(() => {
    const element = create(of([conversation(1), conversation(5, { with: null, group: { title: 'Mission Kafka', members: [], size: 3,
      creator: 'camille', admin: true } })]));
    const component = fixture.componentInstance;
    expect(element.querySelector('[data-conversation="5"]')?.textContent).toContain('Mission Kafka');
    expect(element.querySelector('[data-conversation="5"] .group-avatar')).not.toBeNull();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const open = spyOn(GroupDialogComponent, 'open').and.returnValue(of(undefined));
    (element.querySelector('[data-messages-new-group]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { title: null, present: [], room: 19 });
    expect(api.createGroup).not.toHaveBeenCalled();
    open.and.returnValue(of({ title: 'Équipe', handles: ['leo_m'] }));
    api.createGroup.and.returnValue(of(conversation(9, { with: null, group: { title: 'Équipe', members: [], size: 2, creator: 'camille',
      admin: true } })));
    component.newGroup();
    expect(api.createGroup).toHaveBeenCalledWith('Équipe', ['leo_m']);
    expect(navigate).toHaveBeenCalledWith(['/network/messages', 9]);
    expect(component.conversations[0].id).toBe(9);
    api.createGroup.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.newGroup();
    expect(notifications.error).toHaveBeenCalledWith('network.groups.refused');
    api.createGroup.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.newGroup();
    expect(notifications.error).toHaveBeenCalledWith('network.groups.error');
    component.left(5);
    expect(component.conversations.map(item => item.id)).not.toContain(5);
    expect(navigate).toHaveBeenCalledWith(['/network/messages']);
    discardPeriodicTasks();
  }));

  it('writes to one member picked from those followed or following, opening their conversation', fakeAsync(() => {
    const element = create(of([conversation(1)]));
    const component = fixture.componentInstance;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const open = spyOn(GroupDialogComponent, 'open').and.returnValue(of(undefined));
    (element.querySelector('[data-messages-new]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { title: null, present: [], room: 1, direct: true });
    expect(api.open).not.toHaveBeenCalled();
    open.and.returnValue(of({ title: '', handles: ['leo_m'] }));
    api.open.and.returnValue(of(conversation(12)));
    component.newMessage();
    expect(api.open).toHaveBeenCalledWith('leo_m');
    expect(navigate).toHaveBeenCalledWith(['/network/messages', 12]);
    expect(component.conversations[0].id).toBe(12);
    api.open.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.newMessage();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.openError');
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

  it('names a conversation with a former member as such', () => {
    const element = create(of([conversation(1, { with: null })]));
    expect(element.querySelector('[data-conversation="1"]')?.textContent).toContain('Former member');
  });
});

import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';

import { Conversation, MemberCard } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkEvent, NetworkStreamService } from '../../../services/network/network-stream.service';
import { DOCK_REFRESH_MS, DOCK_SETTLE_MS, MessengerDockComponent } from './messenger-dock.component';
import { MessageThreadComponent } from '../messages/message-thread.component';

@Component({ template: '' })
class BlankComponent {}

/** The conversation itself, tested on its own: here only what the window gives it. */
@Component({ selector: 'app-message-thread', template: '' })
class ThreadStubComponent {
  @Input() conversationId!: number;
  @Input() compact = false;
  @Output() readonly changed = new EventEmitter<Conversation>();
  @Output() readonly left = new EventEmitter<number>();
}

describe('MessengerDockComponent', () => {
  let fixture: ComponentFixture<MessengerDockComponent>;
  let api: jasmine.SpyObj<NetworkMessagesService>;
  let events: Subject<NetworkEvent>;
  const card = (handle: string, firstName: string): MemberCard => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const conversation = (id: number, extra: Partial<Conversation> = {}): Conversation => ({ id, with: card(`m${id}`, `Léo${id}`), group: null,
    last: { mine: false, text: `Message ${id}`, deleted: false, sentAt: '2026-11-18T09:00:00' }, unread: 0, muted: false, archived: false,
    closed: null, lastMessageAt: `2026-11-18T09:0${id}:00`, online: false, ...extra });

  function create(answer = of([conversation(1, { unread: 2, online: true }), conversation(2), conversation(3, { unread: 1, muted: true })])) {
    api = jasmine.createSpyObj('NetworkMessagesService', ['conversations']);
    api.conversations.and.returnValue(answer);
    events = new Subject<NetworkEvent>();
    TestBed.configureTestingModule({
      imports: [MessengerDockComponent, TranslateModule.forRoot()],
      providers: [provideRouter([{ path: '**', component: BlankComponent }]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkMessagesService, useValue: api },
        { provide: NetworkStreamService, useValue: { events: events.asObservable(), connected: true } }]
    });
    TestBed.overrideComponent(MessengerDockComponent, { remove: { imports: [MessageThreadComponent] }, add: { imports: [ThreadStubComponent] } });
    fixture = TestBed.createComponent(MessengerDockComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('counts the unread conversations, the muted aside, and lists them with who is online', fakeAsync(() => {
    const element = create();
    expect(api.conversations).toHaveBeenCalledWith(false, true);
    expect(element.querySelector('[data-dock-unread]')?.textContent?.trim()).toBe('1');
    expect(element.querySelector('[data-dock-list]')).toBeNull();
    (element.querySelector('[data-dock-toggle]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-dock-conversation]').length).toBe(3);
    expect(element.querySelectorAll('[data-dock-list] .online-dot').length).toBe(1);
    expect(element.querySelector('[data-dock-all]')?.getAttribute('href')).toBe('/network/messages');
    discardPeriodicTasks();
  }));

  it('opens a conversation as a chat window, folds and closes it, a few at once', fakeAsync(() => {
    const element = create();
    const component = fixture.componentInstance;
    spyOnProperty(window, 'innerWidth').and.returnValue(1440);
    component.toggleList();
    fixture.detectChanges();
    (element.querySelector('[data-dock-conversation="1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const chat = element.querySelector('[data-dock-chat="1"]')!;
    expect(chat.textContent).toContain('Léo1 Martin');
    expect(chat.querySelector('[data-dock-online]')).not.toBeNull();
    expect(chat.querySelector('app-message-thread')).not.toBeNull();
    const thread = fixture.debugElement.query(debug => debug.componentInstance instanceof ThreadStubComponent).componentInstance as ThreadStubComponent;
    expect(thread.conversationId).toBe(1);
    expect(thread.compact).toBeTrue();
    expect(chat.querySelector('[data-dock-full]')?.getAttribute('href')).toBe('/network/messages/1');
    // Opened again, it stays one window; folded, its body goes.
    component.openChat(1);
    expect(component.windows.length).toBe(1);
    component.fold(component.windows[0]);
    fixture.detectChanges();
    expect(chat.querySelector('app-message-thread')).toBeNull();
    // Three at most on a wide screen, the oldest closing first; fewer once the screen narrows.
    component.openChat(2);
    component.openChat(3);
    component.openChat(4);
    expect(component.windows.map(window => window.id)).toEqual([2, 3, 4]);
    (window.innerWidth as unknown as jasmine.Spy) = 700 as never;
    component.close(3);
    expect(component.windows.map(window => window.id)).toEqual([2, 4]);
    component.left(4);
    expect(component.windows.map(window => window.id)).toEqual([2]);
    expect(component.conversations.map(item => item.id)).not.toContain(4);
    discardPeriodicTasks();
  }));

  it('follows the stream and reads the list again every so often, for who is online', fakeAsync(() => {
    create();
    events.next({ type: 'message', conversationId: 1 });
    events.next({ type: 'read', conversationId: 1 });
    tick(DOCK_SETTLE_MS);
    expect(api.conversations).toHaveBeenCalledTimes(2);
    tick(DOCK_REFRESH_MS);
    expect(api.conversations).toHaveBeenCalledTimes(3);
    const component = fixture.componentInstance;
    component.changed(conversation(2, { lastMessageAt: '2026-11-18T10:00:00' }));
    expect(component.conversations[0].id).toBe(2);
    expect(component.title(undefined)).toBe('');
    expect(component.title(conversation(5, { with: null, group: { title: 'Équipe', members: [], size: 3, creator: 'x', admin: false } })))
      .toBe('Équipe');
    discardPeriodicTasks();
  }));

  it('hides on the Messages page, and for a visitor who has not joined the network', fakeAsync(() => {
    let element = create();
    void TestBed.inject(Router).navigateByUrl('/network/messages/1');
    tick();
    fixture.detectChanges();
    expect(element.querySelector('[data-messenger-dock]')).toBeNull();
    void TestBed.inject(Router).navigateByUrl('/projects');
    tick();
    fixture.detectChanges();
    expect(element.querySelector('[data-messenger-dock]')).not.toBeNull();
    discardPeriodicTasks();
    TestBed.resetTestingModule();
    element = create(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect(element.querySelector('[data-messenger-dock]')).toBeNull();
    tick(DOCK_REFRESH_MS);
    expect(api.conversations).toHaveBeenCalledTimes(1);
    discardPeriodicTasks();
    // Out of reach, the server says nothing yet: no corner until the conversations are read.
    TestBed.resetTestingModule();
    element = create(throwError(() => new HttpErrorResponse({ status: 0 })));
    expect(element.querySelector('[data-messenger-dock]')).toBeNull();
    api.conversations.and.returnValue(of([]));
    tick(DOCK_REFRESH_MS);
    fixture.detectChanges();
    expect(element.querySelector('[data-messenger-dock]')).not.toBeNull();
    discardPeriodicTasks();
  }));
});

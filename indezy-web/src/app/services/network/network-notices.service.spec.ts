import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { NetworkService } from './network.service';
import { NetworkMessagesService } from './network-messages.service';
import { NETWORK_NOTICES_REFRESH_MS, NetworkNoticesService } from './network-notices.service';
import { NetworkEvent, NetworkStreamService } from './network-stream.service';

describe('NetworkNoticesService', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let messages: jasmine.SpyObj<NetworkMessagesService>;
  let events: Subject<NetworkEvent>;
  let stream: { events: Subject<NetworkEvent>; connected: boolean; start: jasmine.Spy; stop: jasmine.Spy };

  function create(): NetworkNoticesService {
    network = jasmine.createSpyObj<NetworkService>('NetworkService', ['unreadNotifications']);
    network.unreadNotifications.and.returnValue(of({ unread: 3 }));
    messages = jasmine.createSpyObj<NetworkMessagesService>('NetworkMessagesService', ['unread']);
    messages.unread.and.returnValue(of(2));
    events = new Subject<NetworkEvent>();
    stream = { events, connected: false, start: jasmine.createSpy('start'), stop: jasmine.createSpy('stop') };
    TestBed.configureTestingModule({ providers: [{ provide: NetworkService, useValue: network },
      { provide: NetworkMessagesService, useValue: messages }, { provide: NetworkStreamService, useValue: stream }] });
    return TestBed.inject(NetworkNoticesService);
  }

  it('counts the unread notifications now and every minute, none on a failure or once stopped', fakeAsync(() => {
    const service = create();
    const counts: number[] = [];
    service.unread$.subscribe(count => counts.push(count));
    service.start();
    service.start();
    expect(counts.at(-1)).toBe(3);
    network.unreadNotifications.and.returnValue(throwError(() => new Error('not a member')));
    tick(NETWORK_NOTICES_REFRESH_MS);
    expect(counts.at(-1)).toBe(0);
    expect(network.unreadNotifications).toHaveBeenCalledTimes(3);
    service.stop();
    service.stop();
    tick(NETWORK_NOTICES_REFRESH_MS);
    expect(network.unreadNotifications).toHaveBeenCalledTimes(3);
  }));

  it('counts the conversations with unread messages too, both together for the menu', fakeAsync(() => {
    const service = create();
    const totals: number[] = [];
    const conversations: number[] = [];
    service.total$.subscribe(total => totals.push(total));
    service.messages$.subscribe(count => conversations.push(count));
    service.start();
    expect(conversations.at(-1)).toBe(2);
    expect(totals.at(-1)).toBe(5);
    messages.unread.and.returnValue(throwError(() => new Error('down')));
    service.refreshMessages();
    expect(totals.at(-1)).toBe(3);
    service.stop();
    expect(totals.at(-1)).toBe(0);
  }));

  it('opens the stream and counts again as it tells, polling only while it is closed', fakeAsync(() => {
    const service = create();
    service.start();
    expect(stream.start).toHaveBeenCalled();
    stream.connected = true;
    tick(NETWORK_NOTICES_REFRESH_MS);
    expect(network.unreadNotifications).toHaveBeenCalledTimes(1);
    events.next({ type: 'notice' });
    expect(network.unreadNotifications).toHaveBeenCalledTimes(2);
    expect(messages.unread).toHaveBeenCalledTimes(1);
    events.next({ type: 'message', conversationId: 4 });
    events.next({ type: 'read', conversationId: 4 });
    events.next({ type: 'typing', conversationId: 4, name: 'Léo' });
    expect(messages.unread).toHaveBeenCalledTimes(3);
    service.stop();
    expect(stream.stop).toHaveBeenCalled();
  }));
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NetworkMessagesService } from './network-messages.service';

describe('NetworkMessagesService', () => {
  it('lists, opens, reads and sets conversations, and sends, edits, deletes and reacts to messages', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkMessagesService);
    const http = TestBed.inject(HttpTestingController);
    service.conversations(true).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations')).request.params.get('archived')).toBe('true');
    service.open('leo_m').subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/network/conversations')).request.body).toEqual({ handle: 'leo_m' });
    let unread = -1;
    service.unread().subscribe(count => unread = count);
    http.expectOne(call => call.url.endsWith('/network/conversations/unread')).flush({ conversations: 2 });
    expect(unread).toBe(2);
    service.thread(4).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4')).request.params.has('before')).toBeFalse();
    service.thread(4, 30).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4')).request.params.get('before')).toBe('30');
    service.createGroup('Mission Kafka', ['leo_m']).subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/network/groups')).request.body)
      .toEqual({ title: 'Mission Kafka', handles: ['leo_m'] });
    service.addMembers(4, ['ines_b']).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/groups/4/members')).request.body).toEqual({ handles: ['ines_b'] });
    service.renameGroup(4, 'Équipe').subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/groups/4/title')).request.body).toEqual({ title: 'Équipe' });
    service.leaveGroup(4).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/groups/4/members/me'));
    service.search('kafka').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/search')).request.params.get('q')).toBe('kafka');
    service.pdf(4, 'EN').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4/pdf')).request.params.get('language')).toBe('EN');
    service.read(4).subscribe();
    http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/conversations/4/read'));
    service.settings(4, { muted: true }).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4/settings')).request.body).toEqual({ muted: true });
    service.send(4, 'Bonjour', 7).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4/messages')).request.body)
      .toEqual({ text: 'Bonjour', replyTo: 7, opportunityId: null });
    service.send(4, '', null, 12).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/conversations/4/messages')).request.body)
      .toEqual({ text: '', replyTo: null, opportunityId: 12 });
    service.take(9).subscribe();
    http.expectOne(call => call.method === 'POST' && call.url.endsWith('/network/messages/9/pipeline'));
    service.edit(9, 'Bonsoir').subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/messages/9')).request.body).toEqual({ text: 'Bonsoir' });
    service.remove(9).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/messages/9'));
    service.react(9, 'WOW').subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/messages/9/reaction')).request.params.get('reaction'))
      .toBe('WOW');
    service.react(9, null).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/messages/9/reaction'));
    http.verify();
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NetworkCommunitiesService } from './network-communities.service';

describe('NetworkCommunitiesService', () => {
  it('lists, creates, reads, joins and moderates communities', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkCommunitiesService);
    const http = TestBed.inject(HttpTestingController);
    const at = (method: string, path: string) => http.expectOne(call => call.method === method && call.url.endsWith(path));
    service.list('kafka').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/network/communities')).request.params.get('q')).toBe('kafka');
    service.create('Kafka Lyon', null).subscribe();
    expect(at('POST', '/network/communities').request.body).toEqual({ name: 'Kafka Lyon', description: null, privateCommunity: false });
    service.page('kafka-lyon').subscribe();
    at('GET', '/network/communities/kafka-lyon');
    service.update('kafka-lyon', 'Kafka', 'Les fans', true).subscribe();
    expect(at('PUT', '/network/communities/kafka-lyon').request.body).toEqual({ name: 'Kafka', description: 'Les fans', privateCommunity: true });
    service.requests('kafka-lyon').subscribe();
    at('GET', '/network/communities/kafka-lyon/requests');
    service.answer('kafka-lyon', 'zoe_l', true).subscribe();
    at('PUT', '/network/communities/kafka-lyon/requests/zoe_l');
    service.answer('kafka-lyon', 'zoe_l', false).subscribe();
    at('DELETE', '/network/communities/kafka-lyon/requests/zoe_l');
    service.remove('kafka-lyon').subscribe();
    at('DELETE', '/network/communities/kafka-lyon');
    service.join('kafka-lyon').subscribe();
    at('PUT', '/network/communities/kafka-lyon/membership');
    service.leave('kafka-lyon').subscribe();
    at('DELETE', '/network/communities/kafka-lyon/membership');
    service.posts('kafka-lyon', 2).subscribe();
    expect(at('GET', '/network/communities/kafka-lyon/posts').request.params.get('page')).toBe('2');
    service.post('kafka-lyon', 'Bonjour').subscribe();
    expect(at('POST', '/network/communities/kafka-lyon/posts').request.body).toEqual({ text: 'Bonjour' });
    service.removePost('kafka-lyon', 4).subscribe();
    at('DELETE', '/network/communities/kafka-lyon/posts/4');
    service.members('kafka-lyon').subscribe();
    at('GET', '/network/communities/kafka-lyon/members');
    service.removeMember('kafka-lyon', 'leo_m').subscribe();
    at('DELETE', '/network/communities/kafka-lyon/members/leo_m');
    service.setModerator('kafka-lyon', 'leo_m', true).subscribe();
    at('PUT', '/network/communities/kafka-lyon/moderators/leo_m');
    service.setModerator('kafka-lyon', 'leo_m', false).subscribe();
    at('DELETE', '/network/communities/kafka-lyon/moderators/leo_m');
    http.verify();
  });
});

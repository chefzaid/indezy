import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NetworkService } from './network.service';

describe('NetworkService', () => {
  it('reads, joins and leaves the network, checks handles and reads members\' cards', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkService);
    const http = TestBed.inject(HttpTestingController);
    const fields = { photo: true, headline: true, stack: true, location: false, availability: true, languages: true, remote: true };
    service.membership().subscribe();
    http.expectOne(call => call.method === 'GET' && call.url.endsWith('/network/membership'));
    const availability = { mode: 'LISTENING' as const, date: null };
    service.join('camille', fields, availability).subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/membership')).request.body)
      .toEqual({ handle: 'camille', fields, availability });
    service.verify().subscribe();
    http.expectOne(call => call.method === 'POST' && call.url.endsWith('/network/membership/verification'));
    service.leave().subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/membership'));
    service.handle('cam ille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/handles/cam%20ille'));
    service.member('camille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/members/camille'));
    service.page('camille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/members/camille/page'));
    service.follow('camille').subscribe();
    http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/members/camille/follow'));
    service.unfollow('camille').subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/members/camille/follow'));
    service.followers('camille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/members/camille/followers'));
    service.following('camille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/members/camille/following'));
    service.removeFollower('leo').subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/followers/leo'));
    http.verify();
  });
});

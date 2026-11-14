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
    service.join('camille', fields).subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/network/membership')).request.body)
      .toEqual({ handle: 'camille', fields });
    service.leave().subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/membership'));
    service.handle('cam ille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/handles/cam%20ille'));
    service.member('camille').subscribe();
    http.expectOne(call => call.url.endsWith('/network/members/camille'));
    http.verify();
  });
});

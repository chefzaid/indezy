import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AccountDeletionService } from './account-deletion.service';

describe('AccountDeletionService', () => {
  it('asks for the deletion with the email typed', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    let purgeOn = '';
    TestBed.inject(AccountDeletionService).request('camille@example.test').subscribe(hold => purgeOn = hold.purgeOn);
    const request = http.expectOne(call => call.method === 'POST' && call.url.endsWith('/users/me/deletion'));
    expect(request.request.body).toEqual({ confirmation: 'camille@example.test' });
    request.flush({ purgeOn: '2026-12-17' });
    expect(purgeOn).toBe('2026-12-17');
    http.verify();
  });
});

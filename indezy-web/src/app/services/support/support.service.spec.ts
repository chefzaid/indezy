import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { SupportService } from './support.service';

describe('SupportService', () => {
  it('sends, lists, receives and answers requests', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(SupportService);
    const http = TestBed.inject(HttpTestingController);
    service.send({ kind: 'QUESTION', subject: 'S', description: 'D', context: null }).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/support/requests`).request.body.kind).toBe('QUESTION');
    service.mine().subscribe();
    http.expectOne(`${environment.apiUrl}/support/requests`).flush([]);
    service.received().subscribe();
    http.expectOne(`${environment.apiUrl}/admin/support/requests`).flush([]);
    service.answer(3, 'Fixed.', true).subscribe();
    const answer = http.expectOne(`${environment.apiUrl}/admin/support/requests/3`);
    expect(answer.request.method).toBe('PUT');
    expect(answer.request.body).toEqual({ answer: 'Fixed.', close: true });
    http.verify();
  });
});

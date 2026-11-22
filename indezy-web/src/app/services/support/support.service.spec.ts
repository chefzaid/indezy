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
    service.attach(3, new File(['png'], 'a.png')).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/support/requests/3/attachments`).request.body instanceof FormData).toBeTrue();
    service.file({ id: 9, requestId: 3, fileName: 'a.jpg', contentType: 'image/jpeg', sizeBytes: 1 }).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/support/requests/3/attachments/9`).request.responseType).toBe('blob');
    http.verify();
  });
});

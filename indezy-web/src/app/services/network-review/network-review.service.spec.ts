import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { NetworkReviewService } from './network-review.service';

describe('NetworkReviewService', () => {
  it('reads, writes and deletes the review of a client', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkReviewService);
    const http = TestBed.inject(HttpTestingController);
    const url = `${environment.apiUrl}/network/reviews/client/3`;
    service.company(3).subscribe();
    http.expectOne(url).flush({});
    const draft = { paymentRating: 2, processRating: 4, rateRating: 3, text: 'Paid at 45 days, process quick.' };
    service.write(3, draft).subscribe();
    const written = http.expectOne(url);
    expect(written.request.method).toBe('PUT');
    expect(written.request.body).toEqual(draft);
    written.flush({});
    service.delete(3).subscribe();
    expect(http.expectOne(url).request.method).toBe('DELETE');
    http.verify();
  });
});

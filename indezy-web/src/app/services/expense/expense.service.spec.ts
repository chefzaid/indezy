import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ExpenseDraft } from '../../models';
import { ExpenseService } from './expense.service';

describe('ExpenseService', () => {
  let service: ExpenseService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/expenses`;
  const draft: ExpenseDraft = { spentOn: '2026-03-12', label: 'Train', category: 'TRAVEL', amount: 120, vat: 20, projectId: null, notes: null };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ExpenseService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a year, sums it up, and writes, changes and deletes an expense', () => {
    service.list(2026).subscribe();
    http.expectOne(`${url}?year=2026`).flush([]);
    service.summary(2026).subscribe();
    http.expectOne(`${url}/summary/2026`).flush({});
    service.create(draft).subscribe();
    const created = http.expectOne(url);
    expect(created.request.method).toBe('POST');
    expect(created.request.body).toEqual(draft);
    created.flush({});
    service.update(4, draft).subscribe();
    expect(http.expectOne(`${url}/4`).request.method).toBe('PUT');
    service.delete(4).subscribe();
    expect(http.expectOne(`${url}/4`).request.method).toBe('DELETE');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CompanyService } from './company.service';

describe('CompanyService', () => {
  it('searches the directory by the words typed', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(CompanyService);
    const http = TestBed.inject(HttpTestingController);
    let found: unknown;
    service.search('total energies').subscribe(companies => found = companies);
    const request = http.expectOne(call => call.url.endsWith('/companies/search'));
    expect(request.request.params.get('q')).toBe('total energies');
    request.flush([]);
    expect(found).toEqual([]);
    http.verify();
  });
});

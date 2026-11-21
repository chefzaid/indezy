import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { RateBenchmarkService } from './rate-benchmark.service';

describe('RateBenchmarkService', () => {
  it('reads the skills, then a figure for all or for a seniority and a region', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(RateBenchmarkService);
    const http = TestBed.inject(HttpTestingController);
    const url = `${environment.apiUrl}/network/benchmark`;
    service.skills().subscribe();
    http.expectOne(`${url}/skills`).flush({});
    service.figure('Java', null, null).subscribe();
    http.expectOne(`${url}?skill=Java`).flush({});
    service.figure('Java', 'SENIOR', 'REMOTE').subscribe();
    http.expectOne(`${url}?skill=Java&seniority=SENIOR&region=REMOTE`).flush({});
    http.verify();
  });
});

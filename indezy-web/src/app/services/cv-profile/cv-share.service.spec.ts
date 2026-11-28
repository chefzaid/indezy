import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CvShareService } from './cv-share.service';

describe('CvShareService', () => {
  it('makes, lists and revokes links, and opens one with its token in a header', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(CvShareService);
    const http = TestBed.inject(HttpTestingController);

    service.list().subscribe();
    http.expectOne(call => call.url.endsWith('/users/cv-shares') && call.method === 'GET');
    service.create(3, 'ESN', 'ANONYMOUS', 7).subscribe();
    expect(http.expectOne(call => call.method === 'POST').request.body).toEqual({ profileId: 3, recipient: 'ESN', layout: 'ANONYMOUS', days: 7 });
    service.revoke(5).subscribe();
    http.expectOne(call => call.url.endsWith('/users/cv-shares/5/revoke'));
    service.open('secret').subscribe();
    const opened = http.expectOne(call => call.url.endsWith('/public/cv-shares'));
    expect(opened.request.headers.get('X-Cv-Token')).toBe('secret');
    expect(opened.request.url).not.toContain('secret');
    service.dossier('secret', 'DOCX').subscribe();
    const dossier = http.expectOne(call => call.url.endsWith('/public/cv-shares/dossier'));
    expect(dossier.request.params.get('format')).toBe('DOCX');
    expect(dossier.request.responseType).toBe('blob');
    http.verify();
  });
});

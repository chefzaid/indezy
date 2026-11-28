import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CvProfileService } from './cv-profile.service';

describe('CvProfileService', () => {
  it('lists, writes and draws the CV profiles', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(CvProfileService);
    const http = TestBed.inject(HttpTestingController);
    const details = { name: 'Backend', language: 'FR' as const, headline: null, summary: null, skills: null, missions: null, positions: null };

    service.list().subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles')).request.method).toBe('GET');
    service.sources().subscribe();
    http.expectOne(call => call.url.endsWith('/users/cv-profiles/sources'));
    service.create(details).subscribe();
    expect(http.expectOne(call => call.method === 'POST').request.body).toEqual(details);
    service.update(3, details).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3')).request.method).toBe('PUT');
    service.reviewed(3).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/reviewed')).request.method).toBe('PUT');
    service.duplicate(3).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/duplicate')).request.method).toBe('POST');
    service.archive(3, true).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/archived')).request.body).toEqual({ archived: true });
    service.makeDefault(3).subscribe();
    http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/default'));
    service.audit(3, 'ANONYMOUS').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/audit')).request.params.get('layout')).toBe('ANONYMOUS');
    service.translate(3).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/translate')).request.method).toBe('POST');
    service.remove(3).subscribe();
    expect(http.expectOne(call => call.method === 'DELETE').request.url).toContain('/users/cv-profiles/3');
    service.dossier(3, 'ANONYMOUS', 'DOCX').subscribe();
    const dossier = http.expectOne(call => call.url.endsWith('/users/cv-profiles/3/dossier'));
    expect(dossier.request.params.get('format')).toBe('DOCX');
    expect(dossier.request.params.get('layout')).toBe('ANONYMOUS');
    expect(dossier.request.responseType).toBe('blob');
    http.verify();
  });
});

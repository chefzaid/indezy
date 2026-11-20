import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NetworkIdeasService } from './network-ideas.service';

describe('NetworkIdeasService', () => {
  it('lists, proposes, changes, votes for, answers and merges ideas', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkIdeasService);
    const http = TestBed.inject(HttpTestingController);
    const at = (method: string, path: string) => http.expectOne(call => call.method === method && call.url.endsWith(path));
    service.list('TOP', null).subscribe();
    const all = at('GET', '/network/ideas').request.params;
    expect([all.get('sort'), all.get('q'), all.get('page'), all.has('status')]).toEqual(['TOP', '', '0', false]);
    service.list('NEW', 'PLANNED', 'csv', 2).subscribe();
    const narrowed = at('GET', '/network/ideas').request.params;
    expect([narrowed.get('sort'), narrowed.get('status'), narrowed.get('q'), narrowed.get('page')]).toEqual(['NEW', 'PLANNED', 'csv', '2']);
    service.get(4).subscribe();
    at('GET', '/network/ideas/4');
    service.create('Export CSV', null).subscribe();
    expect(at('POST', '/network/ideas').request.body).toEqual({ title: 'Export CSV', description: null });
    service.update(4, 'Export CSV', 'Des factures').subscribe();
    expect(at('PUT', '/network/ideas/4').request.body).toEqual({ title: 'Export CSV', description: 'Des factures' });
    service.remove(4).subscribe();
    at('DELETE', '/network/ideas/4');
    service.vote(4, true).subscribe();
    at('PUT', '/network/ideas/4/vote');
    service.vote(4, false).subscribe();
    at('DELETE', '/network/ideas/4/vote');
    service.setStatus(4, 'DONE', 'Livrée en 2.3').subscribe();
    expect(at('PUT', '/network/ideas/4/status').request.body).toEqual({ status: 'DONE', note: 'Livrée en 2.3' });
    service.merge(4, 1).subscribe();
    expect(at('PUT', '/network/ideas/4/merge').request.params.get('into')).toBe('1');
    http.verify();
  });
});

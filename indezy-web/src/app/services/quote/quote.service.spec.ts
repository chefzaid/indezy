import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuoteService } from './quote.service';

describe('QuoteService', () => {
  it('writes, sends, settles, invoices and downloads quotes', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(QuoteService);
    const http = TestBed.inject(HttpTestingController);
    const draft = { clientId: 3, language: 'FR' as const, lines: [] };
    service.list().subscribe();
    http.expectOne(call => call.method === 'GET' && call.url.endsWith('/quotes'));
    service.create(draft).subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/quotes')).request.body).toEqual(draft);
    service.update(4, draft).subscribe();
    http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/quotes/4'));
    service.delete(4).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/quotes/4'));
    service.send(4, '2026-11-14').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/quotes/4/send')).request.body).toEqual({ issueDate: '2026-11-14' });
    service.decide(4, false).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/quotes/4/decision')).request.body).toEqual({ accepted: false });
    service.deposit(4).subscribe();
    http.expectOne(call => call.method === 'POST' && call.url.endsWith('/quotes/4/deposit'));
    service.invoice(4).subscribe();
    http.expectOne(call => call.method === 'POST' && call.url.endsWith('/quotes/4/invoice'));
    service.downloadPdf(4).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/quotes/4/pdf')).request.responseType).toBe('blob');
    service.downloadDocx(4).subscribe();
    expect(http.expectOne(call => call.url.endsWith('/quotes/4/docx')).request.responseType).toBe('blob');
    http.verify();
  });
});

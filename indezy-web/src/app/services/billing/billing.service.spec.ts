import { TestBed } from '@angular/core/testing';
import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BillingIdentity } from '../../models';
import { BillingService } from './billing.service';

describe('BillingService', () => {
  let service: BillingService;
  let http: HttpTestingController;
  const file = new File(['x'], 'logo.png', { type: 'image/png' });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BillingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expect1(method: string, end: string) {
    return http.expectOne(call => call.method === method && call.url.endsWith(end));
  }

  it('reads and saves the billing identity', () => {
    service.identity().subscribe();
    expect1('GET', '/users/billing').flush({});
    service.saveIdentity({ legalName: 'Alice' } as BillingIdentity).subscribe();
    expect(expect1('PUT', '/users/billing').request.body).toEqual({ legalName: 'Alice' });
  });

  it('sends the logo, the terms, the font and the letterhead as files, and removes them', () => {
    service.uploadLogo(file).subscribe();
    expect((expect1('POST', '/users/billing/logo').request.body as FormData).get('file')).toBe(file);
    service.uploadTermsFile(file).subscribe();
    expect((expect1('POST', '/users/billing/quote-terms-file').request.body as FormData).get('file')).toBe(file);
    service.uploadFont(file, null).subscribe();
    expect((expect1('POST', '/users/billing/font').request.body as FormData).has('bold')).toBeFalse();
    service.uploadFont(file, file).subscribe();
    expect((expect1('POST', '/users/billing/font').request.body as FormData).get('bold')).toBe(file);
    service.uploadLetterhead(file).subscribe();
    expect1('POST', '/users/billing/letterhead');
    service.removeLogo().subscribe();
    expect1('DELETE', '/users/billing/logo');
    service.removeTermsFile().subscribe();
    expect1('DELETE', '/users/billing/quote-terms-file');
    service.removeFont().subscribe();
    expect1('DELETE', '/users/billing/font');
    service.removeLetterhead().subscribe();
    expect1('DELETE', '/users/billing/letterhead');
  });

  it('reads the files kept as blobs', () => {
    service.logo(3).subscribe();
    expect(expect1('GET', '/users/billing/logos/3').request.responseType).toBe('blob');
    service.termsFile(4).subscribe();
    expect(expect1('GET', '/users/billing/quote-terms-files/4').request.responseType).toBe('blob');
    service.letterhead(5).subscribe();
    expect(expect1('GET', '/users/billing/letterheads/5').request.responseType).toBe('blob');
  });

  it('handles the Word templates: list, placeholders, upload, starter, example, preview and removal', () => {
    service.templates().subscribe();
    expect1('GET', '/users/billing/templates');
    service.placeholders().subscribe();
    expect1('GET', '/users/billing/templates/placeholders');
    service.uploadTemplate('QUOTE', file).subscribe();
    expect1('POST', '/users/billing/templates/QUOTE');
    service.starterTemplate('INVOICE', 'EN', 'MODERN').subscribe();
    const starter = http.expectOne(call => call.url.endsWith('/templates/INVOICE/starter'));
    expect(starter.request.params.get('theme')).toBe('MODERN');
    expect(starter.request.params.get('language')).toBe('EN');
    service.starterTemplate('INVOICE', 'FR').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/templates/INVOICE/starter')).request.params.has('theme')).toBeFalse();
    service.starterExample('QUOTE', 'FR').subscribe();
    expect(expect1('GET', '/templates/QUOTE/example').request.params.get('language')).toBe('FR');
    service.templatePreview('INVOICE').subscribe();
    expect1('GET', '/invoices/template-preview');
    service.templatePreview('QUOTE').subscribe();
    expect1('GET', '/quotes/template-preview');
    service.removeTemplate('INVOICE').subscribe();
    expect1('DELETE', '/users/billing/templates/INVOICE');
  });

  it('previews a style page with the number of pages the document has, one when the header is missing', () => {
    const pages: number[] = [];
    service.stylePreview({} as BillingIdentity, true, 2).subscribe(preview => pages.push(preview.pages));
    const request = http.expectOne(call => call.url.endsWith('/invoices/style-preview'));
    expect(request.request.params.get('quote')).toBe('true');
    expect(request.request.params.get('page')).toBe('2');
    request.flush(new Blob(['png']), { headers: new HttpHeaders({ 'X-Preview-Pages': '3' }) });
    service.stylePreview({} as BillingIdentity).subscribe(preview => pages.push(preview.pages));
    http.expectOne(call => call.url.endsWith('/invoices/style-preview')).flush(new Blob(['png']));
    expect(pages).toEqual([3, 1]);
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NetworkFilesService } from './network-files.service';

describe('NetworkFilesService', () => {
  it('uploads a file as a form and drops one not attached yet', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkFilesService);
    const http = TestBed.inject(HttpTestingController);
    service.upload(new File(['x'], 'cv.pdf', { type: 'application/pdf' })).subscribe();
    const upload = http.expectOne(call => call.method === 'POST' && call.url.endsWith('/network/files'));
    expect((upload.request.body as FormData).get('file')).toEqual(jasmine.any(File));
    service.discard(4).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/network/files/4'));
    http.verify();
  });

  it('reads sizes, icons and addresses as the cards show them', () => {
    expect(NetworkFilesService.size(200)).toBe('1 KB');
    expect(NetworkFilesService.size(840 * 1024)).toBe('840 KB');
    expect(NetworkFilesService.size(2.4 * 1024 * 1024)).toBe('2.4 MB');
    expect(NetworkFilesService.icon({ contentType: 'application/pdf' })).toBe('picture_as_pdf');
    expect(NetworkFilesService.icon({ contentType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation' }))
      .toBe('slideshow');
    expect(NetworkFilesService.icon({ contentType: 'application/vnd.oasis.opendocument.spreadsheet' })).toBe('table_chart');
    expect(NetworkFilesService.icon({ contentType: 'application/vnd.oasis.opendocument.text' })).toBe('description');
    expect(NetworkFilesService.address('/public/network-files/4/full?e=1&s=x')).toMatch(/\/api\/public\/network-files\/4\/full\?e=1&s=x$/);
  });
});

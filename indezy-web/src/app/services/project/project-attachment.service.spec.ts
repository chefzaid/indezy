import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { ProjectAttachmentService } from './project-attachment.service';

describe('ProjectAttachmentService', () => {
  let service: ProjectAttachmentService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/projects`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProjectAttachmentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, attaches, reads and deletes an opportunity\'s files', () => {
    service.list(8).subscribe();
    service.upload(8, new File(['%PDF'], 'Brief.pdf')).subscribe();
    service.content(1).subscribe();
    service.delete(1).subscribe();

    http.expectOne({ method: 'GET', url: `${api}/8/attachments` }).flush([]);
    const upload = http.expectOne({ method: 'POST', url: `${api}/8/attachments` });
    expect((upload.request.body as FormData).get('file')).toEqual(jasmine.any(File));
    upload.flush({});
    const content = http.expectOne({ method: 'GET', url: `${api}/attachments/1/content` });
    expect(content.request.responseType).toBe('blob');
    content.flush(new Blob(['%PDF']));
    http.expectOne({ method: 'DELETE', url: `${api}/attachments/1` }).flush(null);
  });
});

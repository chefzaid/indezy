import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { EmailTemplateService } from './email-template.service';

describe('EmailTemplateService', () => {
  let service: EmailTemplateService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/email-templates`;
  const template = { name: 'Relance', subject: 'Suivi', body: 'Bonjour {{firstName}}' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EmailTemplateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, saves, changes and deletes templates', () => {
    service.list().subscribe();
    service.create(template).subscribe();
    service.update(3, template).subscribe();
    service.delete(3).subscribe();

    http.expectOne({ method: 'GET', url: api }).flush([]);
    expect(http.expectOne({ method: 'POST', url: api }).request.body).toEqual(template);
    http.expectOne({ method: 'PUT', url: `${api}/3` }).flush(template);
    http.expectOne({ method: 'DELETE', url: `${api}/3` }).flush(null);
  });
});

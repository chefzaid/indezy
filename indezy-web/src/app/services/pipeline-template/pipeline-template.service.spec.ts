import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { PipelineTemplateService } from './pipeline-template.service';

describe('PipelineTemplateService', () => {
  let service: PipelineTemplateService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/pipeline-templates`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PipelineTemplateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, saves, applies and deletes templates', () => {
    service.list().subscribe();
    service.create('Alten', 8, 5).subscribe();
    service.apply(1, 8).subscribe();
    service.delete(1).subscribe();

    http.expectOne({ method: 'GET', url: api }).flush([]);
    const create = http.expectOne({ method: 'POST', url: api });
    expect(create.request.body).toEqual({ name: 'Alten', projectId: 8, clientId: 5 });
    create.flush({});
    http.expectOne({ method: 'POST', url: `${api}/1/apply/8` }).flush([]);
    http.expectOne({ method: 'DELETE', url: `${api}/1` }).flush(null);
  });

  it('saves steps written out one by one', () => {
    const steps = [{ title: 'Call', checklist: ['Rate stated'] }];
    service.createFromSteps('Through an ESN', steps).subscribe();

    const create = http.expectOne({ method: 'POST', url: `${api}/steps` });
    expect(create.request.body).toEqual({ name: 'Through an ESN', steps });
    create.flush({});
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TrainingService } from './training.service';

describe('TrainingService', () => {
  it('lists, records, changes and deletes trainings', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(TrainingService);
    const http = TestBed.inject(HttpTestingController);
    service.list().subscribe();
    expect(http.expectOne(call => call.method === 'GET' && call.url.endsWith('/trainings'))).toBeTruthy();
    service.create({ title: 'CKA' }).subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/trainings')).request.body).toEqual({ title: 'CKA' });
    service.update(4, { title: 'CKAD' }).subscribe();
    expect(http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/trainings/4'))).toBeTruthy();
    service.delete(4).subscribe();
    expect(http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/trainings/4'))).toBeTruthy();
    http.verify();
  });
});

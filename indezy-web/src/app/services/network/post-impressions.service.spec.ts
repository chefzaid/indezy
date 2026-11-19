import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { Router, provideRouter } from '@angular/router';

import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';
import { IMPRESSIONS_BATCH, IMPRESSIONS_FLUSH_MS, PostImpressionsService } from './post-impressions.service';

@Component({ template: '' })
class BlankComponent {}

describe('PostImpressionsService', () => {
  function create() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      provideRouter([{ path: '**', component: BlankComponent }])] });
    return { service: TestBed.inject(PostImpressionsService), http: TestBed.inject(HttpTestingController) };
  }

  it('sends the posts shown together, once per page read, in the background', fakeAsync(() => {
    const { service, http } = create();
    service.seen(1);
    service.seen(2);
    service.seen(1);
    http.expectNone(call => call.url.endsWith('/network/posts/impressions'));
    tick(IMPRESSIONS_FLUSH_MS);
    const sent = http.expectOne(call => call.url.endsWith('/network/posts/impressions'));
    expect(sent.request.body).toEqual({ ids: [1, 2] });
    expect(sent.request.context.get(SILENT_HTTP_ERRORS)).toBeTrue();
    sent.flush(null, { status: 500, statusText: 'down' });
    // Seen again on the same page: nothing more; on another page, again.
    service.seen(1);
    tick(IMPRESSIONS_FLUSH_MS);
    http.expectNone(call => call.url.endsWith('/network/posts/impressions'));
    void TestBed.inject(Router).navigateByUrl('/elsewhere');
    tick();
    service.seen(1);
    tick(IMPRESSIONS_FLUSH_MS);
    expect(http.expectOne(call => call.url.endsWith('/network/posts/impressions')).request.body).toEqual({ ids: [1] });
    http.verify();
  }));

  it('sends at once when a batch is full, and what waits when the page changes', fakeAsync(() => {
    const { service, http } = create();
    for (let id = 1; id <= IMPRESSIONS_BATCH; id++) {
      service.seen(id);
    }
    expect(http.expectOne(call => call.url.endsWith('/network/posts/impressions')).request.body.ids.length).toBe(IMPRESSIONS_BATCH);
    service.seen(100);
    void TestBed.inject(Router).navigateByUrl('/next');
    tick();
    expect(http.expectOne(call => call.url.endsWith('/network/posts/impressions')).request.body).toEqual({ ids: [100] });
    tick(IMPRESSIONS_FLUSH_MS);
    http.verify();
    service.ngOnDestroy();
  }));
});

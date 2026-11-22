import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { HELP_ARTICLES } from './help-articles';
import { HelpPanelComponent, helpArticleFor } from './help-panel.component';
import { HelpSearchService } from './help-search.service';

describe('HelpPanelComponent', () => {
  it('finds the article of each page of the app, the getting started one elsewhere', () => {
    expect(helpArticleFor('/dashboard').slug).toBe('dashboard');
    expect(helpArticleFor('/projects/42?tab=journal').slug).toBe('pipeline');
    expect(helpArticleFor('/offers/7').slug).toBe('offers');
    expect(helpArticleFor('/contacts/2').slug).toBe('clients-contacts');
    expect(helpArticleFor('/invoices').slug).toBe('invoices-quotes');
    expect(helpArticleFor('/expenses').slug).toBe('invoices-quotes');
    expect(helpArticleFor('/network/messages').slug).toBe('network');
    expect(helpArticleFor('/profile#billing').slug).toBe('account');
    expect(helpArticleFor('/somewhere').slug).toBe('getting-started');
  });

  it('shows the article and closes as it opens the full one', () => {
    const close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [HelpPanelComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MAT_DIALOG_DATA, useValue: { slug: helpArticleFor('/offers').slug } }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(HelpPanelComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/offers.md')).flush('# The offer feed');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-help-panel]').textContent).toContain('The offer feed');
    fixture.componentInstance.close();
    expect(close).toHaveBeenCalled();
  });

  it('searches the help and opens what it finds in the panel', fakeAsync(() => {
    const invoices = HELP_ARTICLES.find(article => article.slug === 'invoices-quotes')!;
    const search = jasmine.createSpy('search').and.callFake((typed: string) => of(typed === 'zzz' ? [] : [{ article: invoices, excerpt: '…a CRA…' }]));
    TestBed.configureTestingModule({
      imports: [HelpPanelComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: HelpSearchService, useValue: { search } },
        { provide: MAT_DIALOG_DATA, useValue: { slug: helpArticleFor('/offers').slug } }, { provide: MatDialogRef, useValue: { close: () => undefined } }]
    });
    const fixture = TestBed.createComponent(HelpPanelComponent);
    const panel = fixture.componentInstance;
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    // The support entries: a problem and a feature prefilled with the page, and the requests sent.
    expect(element.querySelector('[data-help-panel-problem]')!.getAttribute('href')).toContain('kind=PROBLEM');
    expect(element.querySelector('[data-help-panel-feature]')!.getAttribute('href')).toContain('kind=FEATURE');
    expect(element.querySelector('[data-help-panel-requests]')!.getAttribute('href')).toBe('/support#mine');

    panel.query.setValue('c');
    tick(200);
    expect(search).not.toHaveBeenCalled();
    panel.query.setValue('cra');
    tick(200);
    fixture.detectChanges();
    expect(element.querySelector('[data-help-panel-hit]')!.textContent).toContain('…a CRA…');
    element.querySelector<HTMLButtonElement>('[data-help-panel-hit="invoices-quotes"]')!.click();
    tick(200);
    fixture.detectChanges();
    expect(panel.article).toBe(invoices);
    expect(panel.hits).toBeNull();
    expect(panel.query.value).toBe('');

    panel.query.setValue('zzz');
    tick(200);
    fixture.detectChanges();
    expect(element.querySelector('[data-help-panel-no-hit]')).not.toBeNull();
    TestBed.inject(HttpTestingController).match(() => true);
  }));

  it('scrolls to the section it was opened on, once', async () => {
    TestBed.configureTestingModule({
      imports: [HelpPanelComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MAT_DIALOG_DATA, useValue: { slug: 'offers', section: { en: 'filter-and-sort', fr: 'filtrer-et-trier' } } },
        { provide: MatDialogRef, useValue: { close: () => undefined } }]
    });
    const fixture = TestBed.createComponent(HelpPanelComponent);
    fixture.detectChanges();
    const scroll = spyOn(Element.prototype, 'scrollIntoView');
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/fr/offers.md'))
      .flush('Intro\n\n## Lire le fil\n\nTexte\n\n## Filtrer et trier\n\nTexte');
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(scroll).toHaveBeenCalledTimes(1);
    expect((scroll.calls.mostRecent().object as Element).id).toBe('filtrer-et-trier');
    fixture.componentInstance.reveal();
    await new Promise(resolve => setTimeout(resolve));
    expect(scroll).toHaveBeenCalledTimes(1);
  });
});

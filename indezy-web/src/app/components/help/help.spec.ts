import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

import { AuthService } from '../../services/auth/auth.service';
import en from '../../../assets/i18n/en.json';
import { LegalPageComponent } from '../legal/legal-page.component';
import { HELP_ARTICLES } from './help-articles';
import { HelpArticleComponent } from './help-article.component';
import { HelpCenterComponent, excerptAround } from './help-center.component';

function configure(component: unknown, providers: unknown[] = [], signedIn = true): void {
  TestBed.configureTestingModule({ imports: [component as never, TranslateModule.forRoot()],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      { provide: AuthService, useValue: { isAuthenticated: () => signedIn } }, ...providers as never[]] });
  const translate = TestBed.inject(TranslateService);
  translate.setTranslation('en', en);
  translate.use('en');
}

describe('excerptAround', () => {
  it('quotes the words around the first match, accents and case aside', () => {
    expect(excerptAround('Créez une facture depuis un CRA validé', 'cra', 10)).toBe('…depuis un CRA validé');
    expect(excerptAround('Rien ici', 'facture')).toBeNull();
  });
});

describe('HelpCenterComponent', () => {
  it('lists every article by section, with the legal pages', () => {
    configure(HelpCenterComponent);
    const fixture = TestBed.createComponent(HelpCenterComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-help-article]').length).toBe(HELP_ARTICLES.length);
    expect(element.querySelector('[data-help-article="network"]')?.getAttribute('href')).toBe('/help/network');
    expect(element.querySelector('[data-help-article="network"]')?.textContent).toContain('The member network');
    expect(element.querySelector('[data-help-legal="privacy"]')?.getAttribute('href')).toBe('/legal/privacy');
    expect(element.querySelector('app-public-page-header')).toBeNull();
  });

  it('searches the titles and the articles\' text, the title matches first', fakeAsync(() => {
    configure(HelpCenterComponent, [], false);
    const fixture = TestBed.createComponent(HelpCenterComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-public-page-header')).not.toBeNull();
    fixture.componentInstance.query.setValue('invoice');
    tick(200);
    const http = TestBed.inject(HttpTestingController);
    for (const article of HELP_ARTICLES) {
      http.expectOne(`assets/help/en/${article.slug}.md`).flush(article.slug === 'activity-reports' ? 'Turn an approved timesheet into an **invoice**.' : 'Nothing here.');
    }
    fixture.detectChanges();
    const hits = fixture.componentInstance.hits!;
    expect(hits[0].article.slug).toBe('invoices-quotes');
    expect(hits.map(hit => hit.article.slug)).toContain('activity-reports');
    expect(hits.find(hit => hit.article.slug === 'activity-reports')?.excerpt).toContain('timesheet into an invoice');
    fixture.componentInstance.query.setValue('zzzz');
    tick(200);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-help-none]')).not.toBeNull();
    fixture.componentInstance.query.setValue('');
    tick(200);
    fixture.detectChanges();
    expect(fixture.componentInstance.hits).toBeNull();
  }));
});

describe('HelpArticleComponent', () => {
  it('shows the article asked for, with the previous and next ones, and its sections', () => {
    const params = new BehaviorSubject(convertToParamMap({ slug: 'offers' }));
    configure(HelpArticleComponent, [{ provide: ActivatedRoute, useValue: { paramMap: params } }]);
    const fixture = TestBed.createComponent(HelpArticleComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('assets/help/en/offers.md').flush('## Filters\n\nText\n\n## Saved searches\n\nText');
    fixture.detectChanges();
    expect(element.querySelector('h1')?.textContent).toContain('The offer feed');
    expect(element.querySelector('[data-help-previous]')?.getAttribute('href')).toBe('/help/faq');
    expect(element.querySelector('[data-help-next]')?.getAttribute('href')).toBe('/help/pipeline');
    expect(Array.from(element.querySelectorAll('[data-help-toc] a')).map(link => link.textContent)).toEqual(['Filters', 'Saved searches']);
    params.next(convertToParamMap({ slug: 'nope' }));
    fixture.detectChanges();
    expect(element.querySelector('[data-help-not-found]')).not.toBeNull();
  });
});

describe('LegalPageComponent', () => {
  it('shows the legal text asked for, the notice when unknown, with links to the others', () => {
    const params = new BehaviorSubject(convertToParamMap({ document: 'privacy' }));
    configure(LegalPageComponent, [{ provide: ActivatedRoute, useValue: { paramMap: params } }], false);
    const fixture = TestBed.createComponent(LegalPageComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    TestBed.inject(HttpTestingController).expectOne('assets/legal/en/privacy.md').flush('## Data\n\nText');
    fixture.detectChanges();
    expect(element.querySelector('h1')?.textContent).toContain('Privacy policy');
    expect(element.querySelector('[data-legal-link="privacy"]')?.getAttribute('aria-current')).toBe('page');
    expect(element.querySelector('app-public-page-header')).not.toBeNull();
    params.next(convertToParamMap({ document: 'unknown' }));
    fixture.detectChanges();
    expect(element.querySelector('[data-legal]')?.getAttribute('data-legal')).toBe('notice');
  });
});

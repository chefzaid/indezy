import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { headingId, plainText, renderMarkdown } from './markdown';
import { MarkdownViewComponent } from './markdown-view.component';

describe('renderMarkdown', () => {
  it('gives the second-level headings ids and lists them, and frames the images with their caption', () => {
    const rendered = renderMarkdown('Intro\n\n## Créer une facture\n\n1. **Nouvelle facture**\n\n### Détail\n\n![Le tableau](/assets/help/screens/x-fr.jpg)');
    expect(rendered.headings).toEqual([{ id: 'creer-une-facture', text: 'Créer une facture' }]);
    expect(rendered.html).toContain('<h2 id="creer-une-facture">Créer une facture</h2>');
    expect(rendered.html).toContain('<h3>Détail</h3>');
    expect(rendered.html).toContain('<strong>Nouvelle facture</strong>');
    expect(rendered.html).toContain('<img src="/assets/help/screens/x-fr.jpg" alt="Le tableau" loading="lazy"><figcaption>Le tableau</figcaption>');
  });

  it('reads an article as plain text for the search, and names headings without accents', () => {
    expect(plainText('## Titre\n\n![image](/a.jpg) Voir [la facture](/help/x), **gras**')).toBe('Titre Voir la facture, gras');
    expect(headingId('Écrire à l’ESN !')).toBe('ecrire-a-l-esn');
    expect(renderMarkdown("## La page d'une opportunité").headings).toEqual([{ id: 'la-page-d-une-opportunite', text: "La page d'une opportunité" }]);
  });
});

describe('MarkdownViewComponent', () => {
  function create(language: string) {
    TestBed.configureTestingModule({ imports: [MarkdownViewComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(TranslateService).use(language);
    const fixture = TestBed.createComponent(MarkdownViewComponent);
    fixture.componentRef.setInput('folder', 'help');
    fixture.componentRef.setInput('name', 'network');
    fixture.detectChanges();
    return fixture;
  }

  it('loads the article in the interface\'s language, and follows its links within the app in place', () => {
    const fixture = create('en');
    const http = TestBed.inject(HttpTestingController);
    const headings: unknown[] = [];
    fixture.componentInstance.headings.subscribe(found => headings.push(found));
    http.expectOne('assets/help/en/network.md').flush('## Posts\n\nSee [the offers](/help/offers) or [the site](https://example.org).');
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h2#posts')?.textContent).toBe('Posts');
    expect(headings).toEqual([[{ id: 'posts', text: 'Posts' }]]);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    (element.querySelector('a[href="/help/offers"]') as HTMLAnchorElement).click();
    expect(navigate).toHaveBeenCalledWith('/help/offers');
    const outside = element.querySelector('a[href="https://example.org"]') as HTMLAnchorElement;
    outside.addEventListener('click', event => event.preventDefault());
    outside.click();
    expect(outside.getAttribute('target')).toBe('_blank');
    expect(outside.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('reads French by default, and says when the article cannot be loaded', () => {
    const fixture = create('fr');
    TestBed.inject(HttpTestingController).expectOne('assets/help/fr/network.md').flush('', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-markdown-missing]')).not.toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AuthService } from '../../services/auth/auth.service';
import { LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { HomeComponent } from './home.component';
import en from '../../../assets/i18n/en.json';
import fr from '../../../assets/i18n/fr.json';

describe('HomeComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let navigate: jasmine.Spy;

  function create(language: 'fr' | 'en', signedIn = false): { component: HomeComponent; element: HTMLElement } {
    auth = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'startSsoLogin']);
    auth.isAuthenticated.and.returnValue(signedIn);
    TestBed.configureTestingModule({
      imports: [HomeComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { language } } } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('fr', fr);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => {
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    TestBed.inject(DOCUMENT).head.querySelectorAll('link[rel="canonical"], link[rel="alternate"], script[type="application/ld+json"]')
      .forEach(element => element.remove());
  });

  it('presents Indezy in French at the root, with its title, description, languages and card', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    const prerendered = window.document.createElement('link');
    prerendered.rel = 'canonical';
    prerendered.setAttribute('data-prerendered', '');
    window.document.head.appendChild(prerendered);
    const { element } = create('fr');
    const document = TestBed.inject(DOCUMENT);

    expect(element.querySelector('h1')!.textContent).toBe('Trouvez la mission, décrochez-la au bon TJM, puis menez-la.');
    expect(element.querySelectorAll('[data-feature]').length).toBe(6);
    expect(element.querySelector('[data-sign-in]')!.textContent).toContain('Se connecter');
    expect(element.querySelector('[data-open-dashboard]')).toBeNull();
    expect(TestBed.inject(Title).getTitle()).toContain('Trouvez la mission');
    expect(TestBed.inject(Meta).getTag('name="description"')!.content).toContain('freelances IT');
    expect(TestBed.inject(Meta).getTag('property="og:locale"')!.content).toBe('fr_FR');
    expect(document.documentElement.lang).toBe('fr');
    expect(document.head.querySelectorAll('link[rel="canonical"]').length).toBe(1);
    expect(document.head.querySelector('[data-prerendered]')).toBeNull();
    expect(document.head.querySelector('link[rel="canonical"]')!.getAttribute('href')).toBe(`${location.origin}/`);
    expect(document.head.querySelector('link[hreflang="en"]')!.getAttribute('href')).toBe(`${location.origin}/en`);
    const data = JSON.parse(document.head.querySelector('script[type="application/ld+json"]')!.textContent!);
    expect(data['@type']).toBe('SoftwareApplication');
  });

  it('presents it in English at /en, and opens the dashboard for a signed-in visitor', () => {
    const { component, element } = create('en', true);

    expect(element.querySelector('h1')!.textContent).toBe('Find the mission, win it at the right rate, then run it.');
    expect(element.querySelector('[data-open-dashboard]')!.getAttribute('href')).toBe('/dashboard');
    expect(element.querySelector('[data-sign-in]')).toBeNull();
    expect(TestBed.inject(Meta).getTag('property="og:url"')!.content).toBe(`${location.origin}/en`);

    component.ngOnDestroy();
    expect(TestBed.inject(Title).getTitle()).not.toContain('Find the mission');
    expect(TestBed.inject(Meta).getTag('name="description"')).toBeNull();
    expect(TestBed.inject(DOCUMENT).head.querySelector('link[rel="canonical"]')).toBeNull();
  });

  it('follows an English browser at the root unless a language was chosen', () => {
    spyOnProperty(navigator, 'language').and.returnValue('en-GB');
    create('fr');
    expect(navigate).toHaveBeenCalledWith(['/en'], { replaceUrl: true, queryParamsHandling: 'preserve' });

    TestBed.resetTestingModule();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    create('fr');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps the language chosen and opens the page in it', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    const { component } = create('fr');
    component.switchLanguage('fr');
    expect(navigate).not.toHaveBeenCalled();
    component.switchLanguage('en');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
    expect(navigate).toHaveBeenCalledWith(['/en']);
  });

  it('signs in on this server\'s sign-in page', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    const { component, element } = create('fr');
    component.signIn();
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(element.querySelector('[data-sign-up]')!.getAttribute('href')).toBe('/register');
  });
});

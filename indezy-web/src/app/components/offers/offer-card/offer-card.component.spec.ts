import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import fr from '../../../../assets/i18n/fr.json';
import { Offer } from '../../../models/offer.models';
import { OfferCardComponent } from './offer-card.component';

describe('OfferCardComponent', () => {
  function offer(overrides: Partial<Offer> = {}): Offer {
    return {
      id: 7, site: 'FREE_WORK', siteName: 'Free-Work', url: 'https://www.free-work.com/fr/tech-it/job-mission/java/7',
      title: 'Développeur Java Spring', company: 'Exemple Conseil', excerpt: 'Refonte d\'un SI bancaire…', rateMin: 550, rateMax: 650,
      city: 'Courbevoie', departmentCode: '92', departmentName: 'Hauts-de-Seine', countryCode: 'FR', distanceKm: 8, workMode: 'HYBRID',
      remoteDaysPerWeek: 2, durationMonths: 6, startDate: '2026-11-02', seniority: 'SENIOR',
      skills: ['Java', 'Spring', 'Kafka'], publishedAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(), datePrecision: 'EXACT',
      expired: false, saved: false, hidden: false, seen: false, ...overrides
    };
  }

  function create(value: Offer = offer(), inputs: Record<string, unknown> = {}): ComponentFixture<OfferCardComponent> {
    registerLocaleData(localeFr);
    TestBed.configureTestingModule({
      imports: [OfferCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferCardComponent);
    fixture.componentRef.setInput('offer', value);
    for (const [name, input] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, input);
    }
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferCardComponent>): string {
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  /** Number formats use narrow no-break spaces; compare with plain ones. */
  function spaced(value: string): string {
    return value.replace(/\s+/g, ' ');
  }

  function element(fixture: ComponentFixture<OfferCardComponent>, selector: string): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
  }

  it('shows the site, the facts and the skills of a new offer', () => {
    const fixture = create();

    expect(text(fixture)).toContain('Free-Work Nouvelle il y a 2 heures');
    expect(text(fixture)).toContain('Exemple Conseil');
    expect(spaced(element(fixture, '[data-fact="rate"]').innerText)).toContain('550 € – 650 €/jour');
    expect(spaced(element(fixture, '[data-fact="place"]').innerText)).toContain('Courbevoie (92) · 8 km');
    expect(spaced(element(fixture, '[data-fact="remote"]').innerText)).toContain('Hybride · 2 j/sem. de télétravail');
    expect(element(fixture, '[data-fact="duration"]').innerText).toContain('6 mois');
    expect(element(fixture, '[data-fact="start"]').innerText).toContain('Début le 2 nov. 2026');
    expect(element(fixture, '[data-fact="seniority"]').innerText).toContain('Senior');
    expect(text(fixture)).toContain('Java Spring Kafka');
    const link = element(fixture, '.offer-title a') as HTMLAnchorElement;
    expect(link.href).toBe('https://www.free-work.com/fr/tech-it/job-mission/java/7');
    expect(link.rel).toBe('noopener noreferrer');
    expect(link.target).toBe('_blank');
    expect(element(fixture, '.offer-card').classList).toContain('unseen');
  });

  it('describes partial rates, places and remote policies', () => {
    const fixture = create(offer({
      rateMin: undefined, rateMax: 600, city: undefined, departmentCode: undefined, departmentName: undefined, distanceKm: undefined,
      workMode: 'REMOTE',
      durationMonths: 1, startDate: undefined, startAsap: true, seniority: undefined, seen: true, expired: true,
      regionName: 'Occitanie', skills: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j']
    }));

    expect(spaced(element(fixture, '[data-fact="rate"]').innerText)).toContain('≤ 600 €');
    expect(element(fixture, '[data-fact="place"]').innerText).toContain('Occitanie');
    expect(element(fixture, '[data-fact="remote"]').innerText).toContain('Full remote');
    expect(element(fixture, '[data-fact="duration"]').innerText).toContain('1 mois');
    expect(element(fixture, '[data-fact="start"]').innerText).toContain('Dès que possible');
    expect(text(fixture)).toContain('Expirée');
    expect(text(fixture)).toContain('+2');
    expect(text(fixture)).not.toContain('Nouvelle');

    const component = fixture.componentInstance;
    component.offer = offer({ rateMin: 500, rateMax: undefined, city: 'Bruxelles', countryCode: 'BE', workMode: 'HYBRID', remoteDaysPerWeek: undefined });
    expect(spaced(component.rate)).toBe('≥ 500 €');
    expect(component.place).toBe('Bruxelles');
    expect(component.remoteKey).toBe('offers.card.remote.HYBRID');
    component.offer = offer({ rateMin: 600, rateMax: 600, city: undefined, departmentName: undefined, countryCode: 'CH' });
    expect(spaced(component.rate)).toBe('600 €');
    expect(component.place).toBe('CH');
    component.offer = offer({ rateMin: undefined, rateMax: undefined, city: undefined, departmentName: undefined, countryCode: 'FR' });
    expect(component.rate).toBe('');
    expect(component.place).toBe('');
  });

  it('says when the offer states no rate', () => {
    const fixture = create(offer({ rateMin: undefined, rateMax: undefined }));

    expect(element(fixture, '[data-fact="rate"]').innerText).toContain('TJM non précisé');
  });

  it('emits what the user does with the offer', () => {
    const fixture = create();
    const card = fixture.componentInstance;
    const events: string[] = [];
    card.toggleDetails.subscribe(() => events.push('details'));
    card.toggleSaved.subscribe(() => events.push('saved'));
    card.toggleHidden.subscribe(() => events.push('hidden'));
    card.opened.subscribe(() => events.push('opened'));
    card.importOffer.subscribe(() => events.push('import'));

    element(fixture, '.details-button').click();
    element(fixture, '.save-button').click();
    element(fixture, '.hide-button').click();
    const link = element(fixture, '.offer-title a');
    link.addEventListener('click', event => event.preventDefault());
    link.click();
    element(fixture, '.import-button').click();

    expect(events).toEqual(['details', 'saved', 'hidden', 'opened', 'import']);
    expect(element(fixture, '.save-button').getAttribute('aria-pressed')).toBe('false');
  });

  it('shows the full description when expanded and links an imported offer to its project', () => {
    const fixture = create(offer({ description: 'Ligne 1\nLigne 2', importedProjectId: 40, saved: true, hidden: true }),
      { expanded: true });

    expect(element(fixture, '[data-description]').textContent).toBe('Ligne 1\nLigne 2');
    expect(element(fixture, '.pipeline-link').getAttribute('href')).toBe('/projects/40');
    expect(element(fixture, '.import-button')).toBeNull();
    expect(element(fixture, '.save-button').getAttribute('aria-label')).toBe('Retirer des offres sauvegardées');
    expect(element(fixture, '.hide-button').getAttribute('aria-label')).toBe('Réafficher l\'offre');
  });

  it('shows the work in progress', () => {
    const fixture = create(offer(), { loadingDetails: true, importing: true });

    expect((element(fixture, '.details-button') as HTMLButtonElement).disabled).toBeTrue();
    expect((element(fixture, '.import-button') as HTMLButtonElement).disabled).toBeTrue();
  });
});

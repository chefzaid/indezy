import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import fr from '../../../../assets/i18n/fr.json';
import { Offer } from '../../../models/offer.models';
import { OfferShareService } from '../../../services/offer/offer-share.service';
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

  let share: jasmine.SpyObj<OfferShareService>;

  function create(value: Offer = offer(), inputs: Record<string, unknown> = {}): ComponentFixture<OfferCardComponent> {
    share = jasmine.createSpyObj('OfferShareService', ['copyLink']);
    registerLocaleData(localeFr);
    TestBed.configureTestingModule({
      imports: [OfferCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: OfferShareService, useValue: share }]
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

  it('shows how well the offer matches the profile, with its skills first', () => {
    const fixture = create(offer({ skills: ['Kafka', 'AWS', 'java'], match: { score: 80, skills: ['Java', 'Spring'], rate: 'MEETS', place: 'NEAR' } }));

    const badge = element(fixture, '[data-match]');
    expect(spaced(badge.innerText)).toContain('80 %');
    expect(badge.classList).toContain('good');
    expect(badge.getAttribute('aria-label'))
      .toBe('Correspond à votre profil à 80 %. Vos compétences : Java, Spring · Atteint votre TJM cible · Près de chez vous');
    const skills = Array.from(fixture.nativeElement.querySelectorAll('.skill') as NodeListOf<HTMLElement>);
    expect(skills.map(skill => skill.innerText.trim())).toEqual(['java', 'Kafka', 'AWS']);
    expect(skills.map(skill => skill.classList.contains('matched'))).toEqual([true, false, false]);
  });

  it('grades weaker matches and shows none without profile skills', () => {
    const fixture = create(offer({ match: { score: 40, skills: [] } }));
    const component = fixture.componentInstance;

    expect(component.matchLevel).toBe('fair');
    expect(component.matchDetails).toBe('Aucune de vos compétences');
    component.offer = offer({ match: { score: 20, skills: ['Java'], rate: 'UNKNOWN', place: 'MOSTLY_REMOTE' } });
    expect(component.matchLevel).toBe('low');
    expect(component.matchDetails).toBe('Vos compétences : Java · TJM non précisé · Loin, mais surtout en télétravail');
    component.offer = offer({ match: { score: 73, skills: ['Java'], availability: 'SOON' } });
    expect(component.matchDetails).toBe('Vos compétences : Java · Démarre jusqu\'à deux semaines avant votre disponibilité');
    fixture.componentRef.setInput('offer', offer());
    fixture.detectChanges();
    expect(element(fixture, '[data-match]')).toBeNull();
    expect(component.matchDetails).toBe('');
    expect(component.isMatched('Java')).toBeFalse();
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

  it('links the same mission on other sites', () => {
    const fixture = create(offer({
      alsoOn: [
        { id: 8, site: 'COLLECTIVE', siteName: 'Collective', url: 'https://www.collective.work/job/8' },
        { id: 9, site: 'WORKDISPO', siteName: 'WorkDispo', url: 'https://www.workdispo.com/job/9' }
      ]
    }));
    const opened: Offer[] = [];
    fixture.componentInstance.opened.subscribe(value => opened.push(value));

    const links = Array.from(fixture.nativeElement.querySelectorAll('[data-also-on] a')) as HTMLAnchorElement[];
    expect(spaced(element(fixture, '[data-also-on]').innerText)).toContain('Aussi sur');
    expect(links.map(link => link.textContent?.trim())).toEqual(['Collective', 'WorkDispo']);
    expect(links[0].href).toBe('https://www.collective.work/job/8');
    expect(links[0].rel).toBe('noopener noreferrer');
    links[0].addEventListener('click', event => event.preventDefault());
    links[0].click();
    expect(opened.map(value => value.id)).toEqual([fixture.componentInstance.offer.id]);
  });

  it('shows no other site for a mission published once', () => {
    expect(create().nativeElement.querySelector('[data-also-on]')).toBeNull();
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

  it('hides every offer of the publisher from its menu, when the offer names one', () => {
    const fixture = create();
    const blocked: Offer[] = [];
    fixture.componentInstance.blockPublisher.subscribe(value => blocked.push(value));

    expect(element(fixture, '.more-button').getAttribute('aria-label')).toBe("Plus d'actions");
    element(fixture, '.more-button').click();
    fixture.detectChanges();
    const item = document.querySelector('[data-block-publisher]') as HTMLButtonElement;
    expect(item.innerText).toContain('Masquer toutes les offres de Exemple Conseil');
    item.click();

    expect(blocked.map(value => value.id)).toEqual([7]);
    fixture.componentRef.setInput('offer', offer({ company: undefined }));
    fixture.detectChanges();
    element(fixture, '.more-button').click();
    fixture.detectChanges();
    expect(document.querySelector('[data-block-publisher]')).toBeNull();
    expect(document.querySelector('[data-edit-note]')).not.toBeNull();
  });

  it('opens the offer\'s own page and copies its link from the menu', () => {
    const fixture = create();

    element(fixture, '.more-button').click();
    fixture.detectChanges();
    expect((document.querySelector('[data-open-offer-page]') as HTMLAnchorElement).getAttribute('href')).toBe('/offers/7');
    (document.querySelector('[data-copy-offer-link]') as HTMLButtonElement).click();

    expect(share.copyLink).toHaveBeenCalledWith(7);
  });

  it('leaves out the details toggle and the selection where they mean nothing', () => {
    const fixture = create(offer({ description: 'Contexte complet' }), { expanded: true, detailsToggle: false, selectable: false });

    expect(element(fixture, '.details-button')).toBeNull();
    expect(element(fixture, '[data-select]')).toBeNull();
    expect(element(fixture, '[data-description]').innerText).toBe('Contexte complet');
  });

  it('adds, edits and deletes a private note from the menu', async () => {
    const fixture = create();
    const saved: { offer: Offer; note: string }[] = [];
    fixture.componentInstance.saveNote.subscribe(value => saved.push(value));
    expect(element(fixture, '[data-note]')).toBeNull();

    element(fixture, '.more-button').click();
    fixture.detectChanges();
    const add = document.querySelector('[data-edit-note]') as HTMLButtonElement;
    expect(add.innerText).toContain('Ajouter une note');
    add.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element(fixture, '[data-delete-note]')).toBeNull();
    const input = element(fixture, '[data-note-editor] textarea') as HTMLTextAreaElement;
    expect(input.maxLength).toBe(2000);
    input.value = 'Rappeler Julie lundi';
    input.dispatchEvent(new Event('input'));
    element(fixture, '[data-save-note]').click();
    fixture.detectChanges();

    expect(saved.map(value => [value.offer.id, value.note])).toEqual([[7, 'Rappeler Julie lundi']]);
    expect(element(fixture, '[data-note-editor]')).toBeNull();
    await new Promise(resolve => setTimeout(resolve));
    expect(document.activeElement).toBe(element(fixture, '.more-button'));

    fixture.componentRef.setInput('offer', offer({ note: 'Rappeler Julie lundi\nTJM 650' }));
    fixture.detectChanges();
    expect(element(fixture, '[data-note]').textContent).toContain('Rappeler Julie lundi\nTJM 650');
    fixture.componentInstance.editNote();
    fixture.detectChanges();
    await fixture.whenStable();
    expect((element(fixture, '[data-note-editor] textarea') as HTMLTextAreaElement).value).toBe('Rappeler Julie lundi\nTJM 650');
    element(fixture, '[data-delete-note]').click();
    fixture.detectChanges();
    expect(saved.at(-1)?.note).toBe('');
  });

  it('shows when and how the workspace applied, and applies or takes the application back from the menu', () => {
    const fixture = create();
    const applied: number[] = [];
    const withdrawn: number[] = [];
    fixture.componentInstance.applyOffer.subscribe(value => applied.push(value.id));
    fixture.componentInstance.withdrawApplication.subscribe(value => withdrawn.push(value.id));
    expect(element(fixture, '[data-applied]')).toBeNull();

    element(fixture, '.more-button').click();
    fixture.detectChanges();
    expect(document.querySelector('[data-withdraw]')).toBeNull();
    (document.querySelector('[data-apply]') as HTMLButtonElement).click();
    expect(applied).toEqual([7]);

    const threeDaysAgo = new Date();
    threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);
    const day = `${threeDaysAgo.getFullYear()}-${`${threeDaysAgo.getMonth() + 1}`.padStart(2, '0')}-` +
      `${`${threeDaysAgo.getDate()}`.padStart(2, '0')}`;
    fixture.componentRef.setInput('offer', offer({ seen: true, application: { appliedOn: day, via: 'EMAIL' } }));
    fixture.detectChanges();
    const badge = element(fixture, '[data-applied]');
    expect(badge.textContent).toContain('Candidature il y a 3 jours');
    expect(badge.getAttribute('aria-label')).toContain('Candidature par e-mail le');
    expect(text(fixture)).not.toContain('Nouvelle');
    element(fixture, '.more-button').click();
    fixture.detectChanges();
    expect(document.querySelector('[data-apply]')).toBeNull();
    (document.querySelector('[data-withdraw]') as HTMLButtonElement).click();
    expect(withdrawn).toEqual([7]);
  });

  it('saves nothing for an unchanged note and closes the editor on Escape', () => {
    const fixture = create(offer({ note: 'Déjà vue' }));
    const saved: unknown[] = [];
    fixture.componentInstance.saveNote.subscribe(value => saved.push(value));

    fixture.componentInstance.editNote();
    fixture.detectChanges();
    fixture.componentInstance.noteDraft = '  Déjà vue ';
    element(fixture, '[data-note-editor]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));
    fixture.detectChanges();
    expect(saved).toEqual([]);
    expect(element(fixture, '[data-note-editor]')).toBeNull();

    fixture.componentInstance.editNote();
    fixture.detectChanges();
    fixture.componentInstance.noteDraft = 'Autre chose';
    element(fixture, '[data-note-editor]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(saved).toEqual([]);
    expect(element(fixture, '[data-note]').textContent).toContain('Déjà vue');
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

  it('is selected with its checkbox', () => {
    const fixture = create();
    const changed: number[] = [];
    fixture.componentInstance.selectedChange.subscribe(value => changed.push(value.id));
    const box = element(fixture, '[data-select] input') as HTMLInputElement;

    expect(box.getAttribute('aria-label')).toBe('Sélectionner « Développeur Java Spring »');
    expect(box.checked).toBeFalse();
    box.click();
    expect(changed).toEqual([7]);
    fixture.componentRef.setInput('selected', true);
    fixture.detectChanges();
    expect(box.checked).toBeTrue();
    expect(element(fixture, '.offer-card').classList).toContain('selected');
  });

  it('shows the work in progress', () => {
    const fixture = create(offer(), { loadingDetails: true, importing: true });

    expect((element(fixture, '.details-button') as HTMLButtonElement).disabled).toBeTrue();
    expect((element(fixture, '.import-button') as HTMLButtonElement).disabled).toBeTrue();
  });
});

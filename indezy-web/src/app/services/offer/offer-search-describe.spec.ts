import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../assets/i18n/en.json';
import fr from '../../../assets/i18n/fr.json';
import { describeOfferSearch, offerSearchName } from './offer-search-describe';

describe('describeOfferSearch', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('fr', fr);
  });

  it('says what each filter of a suggested search keeps', () => {
    translate.use('en');

    expect(describeOfferSearch({ q: 'Tech lead', skills: ['Java', 'Kafka'], rateMin: 1200, radiusKm: 30, workModes: ['REMOTE'],
      startBefore: '2026-12-16' }, 'Lyon', translate, 'en-US'))
      .toEqual(['“Tech lead”', 'Java + Kafka', '1,200 €/day or more', 'Lyon + 30 km', 'Full remote', 'Starting by Dec 16']);
    expect(describeOfferSearch({ skills: ['Java'], workModes: ['HYBRID'], minRemoteDays: 2, radiusKm: 80 }, null, translate, 'en-US'))
      .toEqual(['Java', 'Within 80 km', 'Hybrid, 2+ remote days a week']);
    expect(describeOfferSearch({ regions: ['84'], workModes: ['HYBRID', 'REMOTE'] }, 'Auvergne-Rhône-Alpes', translate, 'en-US'))
      .toEqual(['Auvergne-Rhône-Alpes', 'Hybrid']);
    expect(describeOfferSearch({ regions: ['84'] }, null, translate, 'en-US')).toEqual([]);
  });

  it('speaks the interface language', () => {
    translate.use('fr');

    expect(describeOfferSearch({ q: 'Développeur', rateMin: 1000, startBefore: '2026-12-16' }, null, translate, 'fr-FR'))
      .toEqual(['« Développeur »', '1 000 €/jour ou plus', 'Démarrage d\'ici le 16 déc.']);
  });

  it('names a search after its phrases, as long as a saved search name may be', () => {
    expect(offerSearchName(['Java', 'Lyon + 30 km'])).toBe('Java · Lyon + 30 km');
    const long = offerSearchName(['x'.repeat(50), 'y'.repeat(50)]);
    expect(long.length).toBe(80);
    expect(long.endsWith('…')).toBeTrue();
  });
});

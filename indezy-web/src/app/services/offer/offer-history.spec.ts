import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../assets/i18n/en.json';
import { OfferChange } from '../../models/offer.models';
import { describeOfferChange } from './offer-history';

describe('describeOfferChange', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
  });

  function say(change: Partial<OfferChange>): string {
    return describeOfferChange({ field: 'RATE', changedAt: '2026-10-20T08:00:00Z', ...change } as OfferChange, translate, 'en-US');
  }

  it('says how the rate moved, by the best rate of each range', () => {
    expect(say({ before: '|500', after: '500|550' })).toBe('Rate raised from €500 to €550');
    expect(say({ before: '600|', after: '550|' })).toBe('Rate lowered from €600 to €550');
    expect(say({ after: '|650' })).toBe('Rate stated: €650');
    expect(say({ before: '500|600', after: '550|600' })).toBe('Rate range changed');
    expect(say({ before: '500|600' })).toBe('Rate range changed');
  });

  it('says how the start moved, and a rewritten description', () => {
    expect(say({ field: 'START', before: '2026-11-02', after: '2026-12-01' })).toBe('Start moved to Dec 1, 2026');
    expect(say({ field: 'START', after: '2026-12-01' })).toBe('Start stated: Dec 1, 2026');
    expect(say({ field: 'START', before: '2026-11-02', after: 'ASAP' })).toBe('Now starts as soon as possible');
    expect(say({ field: 'DESCRIPTION' })).toBe('Description rewritten');
  });
});

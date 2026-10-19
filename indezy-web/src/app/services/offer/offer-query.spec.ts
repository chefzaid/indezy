import { convertToParamMap } from '@angular/router';
import {
  countActiveFilters,
  defaultOfferQuery,
  offerQueryFromParams,
  offerQueryToHttpParams,
  offerQueryToParams
} from './offer-query';

describe('offer query', () => {
  it('reads every filter of the page address', () => {
    const query = offerQueryFromParams(convertToParamMap({
      q: '  java spring  ', rateMin: '500', rateMax: '800', rateKnown: 'true', regions: ['11', '84'], departments: '2A,92',
      radiusKm: '30', workModes: ['REMOTE', 'HYBRID'], minRemoteDays: '2', durationMin: '6', durationMax: '24',
      startBefore: '2026-12-01', publishedWithinDays: '7', sites: ['FREE_WORK', 'LITTLE_BIG_CONNECTION'], seniorities: 'SENIOR',
      skills: ['Java', 'Kafka'], unseenOnly: 'true', view: 'SAVED', sort: 'RATE', page: '2', size: '50'
    }));

    expect(query).toEqual({
      q: 'java spring', rateMin: 500, rateMax: 800, rateKnown: true, regions: ['11', '84'], departments: ['2A', '92'],
      radiusKm: 30, workModes: ['REMOTE', 'HYBRID'], minRemoteDays: 2, durationMin: 6, durationMax: 24, startBefore: '2026-12-01',
      publishedWithinDays: 7, sites: ['FREE_WORK', 'LITTLE_BIG_CONNECTION'], seniorities: ['SENIOR'], skills: ['Java', 'Kafka'],
      unseenOnly: true, view: 'SAVED', sort: 'RATE', page: 2, size: 50
    });
  });

  it('ignores what the address gets wrong', () => {
    const query = offerQueryFromParams(convertToParamMap({
      rateMin: '-5', rateMax: 'abc', regions: ['Paris', '11', '11'], departments: '999', workModes: 'BOAT', sites: 'LINKEDIN',
      seniorities: 'GURU', skills: ['', 'x'.repeat(61)], startBefore: '01/12/2026', view: 'ALL', sort: 'RANDOM', page: '-1', size: '13'
    }));

    expect(query).toEqual({ ...defaultOfferQuery(), regions: ['11'], rateKnown: undefined, unseenOnly: undefined });
  });

  it('leaves default values out of the address and repeats list values in API requests', () => {
    const query = { ...defaultOfferQuery(), q: 'java', regions: ['11', '84'], rateKnown: true, page: 1 };

    expect(offerQueryToParams(defaultOfferQuery())).toEqual({});
    expect(offerQueryToParams(query)).toEqual({ q: 'java', regions: ['11', '84'], rateKnown: 'true', page: '1' });
    expect(offerQueryToHttpParams(query).toString()).toBe('regions=11&regions=84&page=1&q=java&rateKnown=true');
  });

  it('counts the filters that narrow the feed', () => {
    expect(countActiveFilters(defaultOfferQuery())).toBe(0);
    expect(countActiveFilters({ ...defaultOfferQuery(), q: 'java', rateMin: 500, sites: ['YEETS'], skills: ['A', 'B'], unseenOnly: true }))
      .toBe(5);
  });
});

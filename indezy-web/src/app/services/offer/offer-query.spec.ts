import { convertToParamMap } from '@angular/router';
import {
  countActiveFilters,
  defaultOfferQuery,
  leavesView,
  offerQueryFromParams,
  offerQueryFromSaved,
  offerQueryToHttpParams,
  offerQueryToParams,
  sameOfferFilters
} from './offer-query';

describe('offer query', () => {
  it('reads the query of a saved search like the page address, on the feed', () => {
    const query = offerQueryFromSaved({
      q: 'kafka', rateMin: 500, rateMax: null, regions: ['11'], workModes: ['REMOTE', 'BOAT'], sites: ['FREE_WORK'],
      rateKnown: true, sort: 'RATE', view: 'SAVED', page: 4, size: 50, startBefore: null
    });

    expect(query).toEqual(offerQueryFromParams(convertToParamMap({
      q: 'kafka', rateMin: '500', regions: ['11'], workModes: ['REMOTE'], sites: ['FREE_WORK'], rateKnown: 'true', sort: 'RATE',
      size: '50'
    })));
    expect(query.view).toBe('FEED');
    expect(query.page).toBe(0);
  });

  it('reads every filter of the page address', () => {
    const query = offerQueryFromParams(convertToParamMap({
      q: '  java spring  ', rateMin: '500', rateMax: '800', rateKnown: 'true', regions: ['11', '84'], departments: '2A,92',
      radiusKm: '30', workModes: ['REMOTE', 'HYBRID'], minRemoteDays: '2', durationMin: '6', durationMax: '24',
      startBefore: '2026-12-01', publishedWithinDays: '7', sites: ['FREE_WORK', 'SIXIEME_HOMME'], seniorities: 'SENIOR',
      skills: ['Java', 'Kafka'], unseenOnly: 'true', matchesProfile: 'true', hideSuspicious: 'true', view: 'SAVED', sort: 'MATCH',
      page: '2', size: '50'
    }));

    expect(query).toEqual({
      q: 'java spring', rateMin: 500, rateMax: 800, rateKnown: true, regions: ['11', '84'], departments: ['2A', '92'],
      radiusKm: 30, workModes: ['REMOTE', 'HYBRID'], minRemoteDays: 2, durationMin: 6, durationMax: 24, startBefore: '2026-12-01',
      publishedWithinDays: 7, sites: ['FREE_WORK', 'SIXIEME_HOMME'], seniorities: ['SENIOR'], skills: ['Java', 'Kafka'],
      unseenOnly: true, matchesProfile: true, hideSuspicious: true, view: 'SAVED', sort: 'MATCH', page: 2, size: 50
    });
  });

  it('ignores what the address gets wrong', () => {
    const query = offerQueryFromParams(convertToParamMap({
      rateMin: '-5', rateMax: 'abc', regions: ['Paris', '11', '11'], departments: '999', workModes: 'BOAT', sites: 'LINKEDIN',
      seniorities: 'GURU', skills: ['', 'x'.repeat(61)], startBefore: '01/12/2026', view: 'ALL', sort: 'RANDOM', page: '-1', size: '13'
    }));

    expect(query).toEqual({ ...defaultOfferQuery(), regions: ['11'], rateKnown: undefined, unseenOnly: undefined, matchesProfile: undefined,
      hideSuspicious: undefined });
  });

  it('tells which changes take an offer out of its view', () => {
    expect(leavesView('FEED', { hidden: true })).toBeTrue();
    expect(leavesView('FEED', { saved: true, seen: true })).toBeFalse();
    expect(leavesView('SAVED', { saved: false })).toBeTrue();
    expect(leavesView('SAVED', { hidden: true })).toBeFalse();
    expect(leavesView('HIDDEN', { hidden: false })).toBeTrue();
    expect(leavesView('APPLIED', { hidden: true, saved: false })).toBeFalse();
  });

  it('reads every view of the feed', () => {
    for (const view of ['FEED', 'SAVED', 'APPLIED', 'HIDDEN']) {
      expect(offerQueryFromParams(convertToParamMap({ view })).view).toBe(view);
    }
  });

  it('leaves default values out of the address and repeats list values in API requests', () => {
    const query = { ...defaultOfferQuery(), q: 'java', regions: ['11', '84'], rateKnown: true, page: 1 };

    expect(offerQueryToParams(defaultOfferQuery())).toEqual({});
    expect(offerQueryToParams(query)).toEqual({ q: 'java', regions: ['11', '84'], rateKnown: 'true', page: '1' });
    expect(offerQueryToHttpParams(query).toString()).toBe('regions=11&regions=84&page=1&q=java&rateKnown=true');
  });

  it('counts the filters that narrow the feed', () => {
    expect(countActiveFilters(defaultOfferQuery())).toBe(0);
    expect(countActiveFilters({ ...defaultOfferQuery(), q: 'java', rateMin: 500, sites: ['YEETS'], skills: ['A', 'B'], unseenOnly: true,
      matchesProfile: true })).toBe(6);
    expect(countActiveFilters({ ...defaultOfferQuery(), hideSuspicious: true })).toBe(1);
  });

  it('tells another page, page size or sort of the same filters from other filters', () => {
    const java = { ...defaultOfferQuery(), q: 'java', sites: ['FREE_WORK' as const, 'YEETS' as const] };

    expect(sameOfferFilters(java, { ...java, page: 3, size: 50, sort: 'RATE' })).toBeTrue();
    expect(sameOfferFilters(java, { ...java, sites: ['YEETS', 'FREE_WORK'] })).toBeFalse();
    expect(sameOfferFilters(java, { ...java, q: 'kotlin' })).toBeFalse();
    expect(sameOfferFilters(java, { ...java, view: 'SAVED' })).toBeFalse();
    expect(sameOfferFilters(java, { ...java, unseenOnly: true })).toBeFalse();
  });
});

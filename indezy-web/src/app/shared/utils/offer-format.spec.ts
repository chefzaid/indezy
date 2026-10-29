import { isMatchedSkill, offerDurationKey, offerPlace, offerRate, offerRemoteKey, offerSkillsMatchedFirst } from './offer-format';

describe('offer format', () => {
  const plain = (value: string): string => value.replace(/\s/g, ' ');

  it('writes the daily rate the offer states', () => {
    expect(plain(offerRate({ rateMin: 550, rateMax: 650 }, 'fr-FR'))).toBe('550 € – 650 €');
    expect(plain(offerRate({ rateMin: 600, rateMax: 600 }, 'fr-FR'))).toBe('600 €');
    expect(plain(offerRate({ rateMax: 600 }, 'fr-FR'))).toBe('≤ 600 €');
    expect(offerRate({ rateMin: 500 }, 'en-US')).toBe('≥ €500');
    expect(offerRate({}, 'fr-FR')).toBe('');
  });

  it('writes the place the offer names', () => {
    expect(offerPlace({ city: 'Courbevoie', departmentCode: '92', countryCode: 'FR' })).toBe('Courbevoie (92)');
    expect(offerPlace({ city: 'Bruxelles', countryCode: 'BE' })).toBe('Bruxelles');
    expect(offerPlace({ departmentName: 'Hauts-de-Seine' })).toBe('Hauts-de-Seine');
    expect(offerPlace({ regionName: 'Île-de-France' })).toBe('Île-de-France');
    expect(offerPlace({ countryCode: 'CH' })).toBe('CH');
    expect(offerPlace({ countryCode: 'FR' })).toBe('');
  });

  it('says how the mission is worked and for how long', () => {
    expect(offerRemoteKey({ workMode: 'HYBRID', remoteDaysPerWeek: 2 })).toBe('offers.card.remote.hybridDays');
    expect(offerRemoteKey({ workMode: 'HYBRID' })).toBe('offers.card.remote.HYBRID');
    expect(offerRemoteKey({ workMode: 'REMOTE' })).toBe('offers.card.remote.REMOTE');
    expect(offerDurationKey({ durationMonths: 1 })).toBe('offers.card.oneMonth');
    expect(offerDurationKey({ durationMonths: 6 })).toBe('offers.card.months');
  });

  it('puts the skills the profile names first, case and accents aside', () => {
    const offer = { skills: ['Kafka', 'Java', 'Réseau', 'Spring'], match: { score: 70, skills: ['java', 'reseau'] } };

    expect(isMatchedSkill(offer, 'Réseau')).toBeTrue();
    expect(isMatchedSkill(offer, 'Kafka')).toBeFalse();
    expect(offerSkillsMatchedFirst(offer, 3)).toEqual(['Java', 'Réseau', 'Kafka']);
    expect(offerSkillsMatchedFirst({ skills: ['Go'] }, 6)).toEqual(['Go']);
  });
});

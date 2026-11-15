import { Offer, ProjectDto } from '../../../models';
import { offerShare, projectShare } from './network-shares';

describe('network shares', () => {
  it('passes an offer on as a mission with its link, its publisher left out', () => {
    const offer = { id: 3, url: 'https://example.test/offre/3', title: 'Lead Java', company: 'Acme', rateMin: 600, rateMax: 700,
      city: 'Lyon', remoteDaysPerWeek: 2, startDate: '2027-01-04', skills: ['Java', 'Kafka'] } as Offer;
    expect(offerShare(offer, 'Repérée :')).toEqual({ text: 'Repérée :\nhttps://example.test/offre/3', kind: 'MISSION', details: {
      role: 'Lead Java', rateMin: 600, rateMax: 700, city: 'Lyon', remoteDays: 2, start: '2027-01-04', client: null, stack: ['Java', 'Kafka'] } });
    expect(offerShare({ ...offer, rateMin: undefined, city: undefined, skills: [] } as Offer, '').details)
      .toEqual(jasmine.objectContaining({ rateMin: null, city: null, stack: [] }));
  });

  it('passes a declined opportunity on as a mission, its client left out', () => {
    const project = { role: 'Architecte', dailyRate: 750, techStack: 'Java, , Kafka', remoteDaysPerMonth: 9, workAddress: '69002 Lyon',
      startDate: '2027-02-01', clientName: 'Acme' } as ProjectDto;
    expect(projectShare(project, 'Je ne la prends pas.')).toEqual({ text: 'Je ne la prends pas.', kind: 'MISSION', details: {
      role: 'Architecte', rateMin: null, rateMax: 750, city: '69002 Lyon', remoteDays: 2, start: '2027-02-01', client: null,
      stack: ['Java', 'Kafka'] } });
    expect(projectShare({ ...project, dailyRate: 0, remoteDaysPerMonth: undefined, techStack: undefined,
      workAddress: undefined, startDate: undefined } as ProjectDto, '').details).toEqual(jasmine.objectContaining({ rateMax: null,
      remoteDays: null, city: null, start: null, stack: [] }));
  });
});

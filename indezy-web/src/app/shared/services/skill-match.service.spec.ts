import { TestBed } from '@angular/core/testing';
import { SkillMatchService } from './skill-match.service';

describe('SkillMatchService', () => {
  let service: SkillMatchService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SkillMatchService);
  });

  it('parses comma-separated tech stacks into tags', () => {
    expect(service.parseTags('Angular, Spring Boot , , Docker')).toEqual(['Angular', 'Spring Boot', 'Docker']);
    expect(service.parseTags('')).toEqual([]);
    expect(service.parseTags(null)).toEqual([]);
  });

  it('scores the share of matching tags case-insensitively', () => {
    expect(service.matchScore('Angular, Java, Docker', ['angular', 'java'])).toBe(67);
    expect(service.matchScore('Angular, Java', ['Angular', 'Java'])).toBe(100);
    expect(service.matchScore('Angular', ['React'])).toBe(0);
  });

  it('returns 0 when there are no tags or no skills', () => {
    expect(service.matchScore('', ['Angular'])).toBe(0);
    expect(service.matchScore('Angular', [])).toBe(0);
    expect(service.matchScore('Angular', null)).toBe(0);
  });
});

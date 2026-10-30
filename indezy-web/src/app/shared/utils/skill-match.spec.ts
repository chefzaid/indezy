import { parseTechTags, skillMatchScore } from './skill-match';

describe('skill match', () => {
  it('parses comma-separated tech stacks into tags', () => {
    expect(parseTechTags('Angular, Spring Boot , , Docker')).toEqual(['Angular', 'Spring Boot', 'Docker']);
    expect(parseTechTags('')).toEqual([]);
    expect(parseTechTags(null)).toEqual([]);
  });

  it('scores the share of matching tags case-insensitively', () => {
    expect(skillMatchScore('Angular, Java, Docker', ['angular', 'java'])).toBe(67);
    expect(skillMatchScore('Angular, Java', ['Angular', 'Java'])).toBe(100);
    expect(skillMatchScore('Angular', ['React'])).toBe(0);
  });

  it('returns 0 when there are no tags or no skills', () => {
    expect(skillMatchScore('', ['Angular'])).toBe(0);
    expect(skillMatchScore('Angular', [])).toBe(0);
    expect(skillMatchScore('Angular', null)).toBe(0);
  });
});

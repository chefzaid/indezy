import { Injectable } from '@angular/core';

/**
 * Computes how well an opportunity's tech stack matches the freelancer's skill profile,
 * and parses comma-separated tech stacks into individual skill tags.
 */
@Injectable({ providedIn: 'root' })
export class SkillMatchService {
  /** Splits a comma-separated tech stack into trimmed, non-empty tags. */
  parseTags(techStack: string | undefined | null): string[] {
    return (techStack ?? '')
      .split(',')
      .map(tag => tag.trim())
      .filter(tag => tag.length > 0);
  }

  /** Percentage (0–100) of the opportunity's tags that appear in the freelancer's skills. */
  matchScore(techStack: string | undefined | null, skills: string[] | undefined | null): number {
    const tags = this.parseTags(techStack);
    if (tags.length === 0) {
      return 0;
    }
    const skillSet = new Set((skills ?? []).map(skill => skill.trim().toLowerCase()).filter(skill => skill.length > 0));
    if (skillSet.size === 0) {
      return 0;
    }
    const matches = tags.filter(tag => skillSet.has(tag.toLowerCase())).length;
    return Math.round((matches / tags.length) * 100);
  }
}

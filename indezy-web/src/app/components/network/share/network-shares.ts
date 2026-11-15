import { Offer, PostDetails, ProjectDto } from '../../../models';
import { NetworkShare } from './share-to-network.component';

const WEEKS_PER_MONTH = 52 / 12;

/** Facts of a mission with no client named: the network sees the mission, not who it is for. */
function facts(role: string, rateMin: number | null, rateMax: number | null, city: string | null, remoteDays: number | null,
               start: string | null, stack: string[]): PostDetails {
  return { role, rateMin, rateMax, city, remoteDays, start, client: null, stack: stack.slice(0, 10) };
}

/** An offer of the feed passed on as a mission: its facts and its link, its publisher left out. */
export function offerShare(offer: Offer, intro: string): NetworkShare {
  return { text: `${intro}\n${offer.url}`, kind: 'MISSION', details: facts(offer.title.slice(0, 100), offer.rateMin ?? null,
    offer.rateMax ?? null, offer.city ?? null, offer.remoteDaysPerWeek ?? null, offer.startDate ?? null, offer.skills) };
}

/** An opportunity declined passed on as a mission: its facts, its client left out. */
export function projectShare(project: ProjectDto, intro: string): NetworkShare {
  const remote = project.remoteDaysPerMonth === undefined || project.remoteDaysPerMonth === null ? null : Math.min(5, Math.round(project.remoteDaysPerMonth / WEEKS_PER_MONTH));
  const stack = (project.techStack ?? '').split(',').map(skill => skill.trim()).filter(skill => skill !== '');
  return { text: intro, kind: 'MISSION', details: facts(project.role.slice(0, 100), null, project.dailyRate || null,
    project.workAddress ?? null, remote, project.startDate ?? null, stack) };
}

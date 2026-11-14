import { CommuteInfoDto, CommuteUnavailable, ProjectCommuteDto } from '../../../models/commute.models';
import { commuteDistance, commuteRange } from '../../../shared/utils/trip-time';

/** What an opportunity's card shows of its commute: the distance and both ways' trips, or why there is none. */
export interface CardCommute {
  distance: string | null;
  car: string | null;
  transit: string | null;
  estimated: boolean;
  unavailable: CommuteUnavailable | null;
}

export function byProject(results: ProjectCommuteDto[]): Map<number, CommuteInfoDto> {
  return new Map(results.filter(result => result.commute && result.project.id)
    .map(result => [result.project.id!, result.commute!] as const));
}

/** Each opportunity's card commute from its trips by car and by public transport. */
export function cardCommutes(car: Map<number, CommuteInfoDto>, transit: Map<number, CommuteInfoDto>): Map<number, CardCommute> {
  const cards = new Map<number, CardCommute>();
  for (const id of new Set([...car.keys(), ...transit.keys()])) {
    const byCar = car.get(id);
    const byTransit = transit.get(id);
    const known = [byCar, byTransit].filter(trip => commuteRange(trip) !== null);
    cards.set(id, {
      distance: commuteDistance(byCar) ?? commuteDistance(byTransit),
      car: commuteRange(byCar),
      transit: commuteRange(byTransit),
      estimated: known.some(trip => trip!.estimated),
      unavailable: known.length > 0 ? null : (byCar?.unavailable ?? byTransit?.unavailable ?? 'LOOKUP_FAILED')
    });
  }
  return cards;
}

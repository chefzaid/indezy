import { commuteDistance, commuteRange, tripRange, tripTime } from './trip-time';
import { CommuteInfoDto } from '../../models/commute.models';

describe('trip time', () => {
  it('writes minutes below an hour, hours and padded minutes above', () => {
    expect(tripTime(7.4)).toBe('7 min');
    expect(tripTime(59.6)).toBe('1 h 00');
    expect(tripTime(105)).toBe('1 h 45');
  });

  it('spans off-peak to rush hour, once when they match', () => {
    expect(tripRange(49, 105)).toBe('≈ 49 min – 1 h 45');
    expect(tripRange(20, 20.2)).toBe('≈ 20 min');
    expect(tripRange(49, 105, false)).toBe('49 min – 1 h 45');
  });

  it('writes an opportunity\'s trip and distance, approximate when estimated', () => {
    const trip = { durationInSeconds: 1080, rushDurationInSeconds: 1560, distanceInMeters: 23_400, estimated: true } as CommuteInfoDto;
    expect(commuteRange(trip)).toBe('≈ 18 min – 26 min');
    expect(commuteDistance(trip)).toBe('≈ 23 km');
    const measured = { durationInSeconds: 1080, rushDurationInSeconds: null, distanceInMeters: 23_400, distanceText: '23,4 km' } as CommuteInfoDto;
    expect(commuteRange(measured)).toBe('18 min');
    expect(commuteDistance(measured)).toBe('23,4 km');
    expect(commuteRange({ unavailable: 'NOT_FOUND' } as CommuteInfoDto)).toBeNull();
    expect(commuteDistance(undefined)).toBeNull();
  });
});

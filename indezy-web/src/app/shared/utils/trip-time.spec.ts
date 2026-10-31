import { tripRange, tripTime } from './trip-time';

describe('trip time', () => {
  it('writes minutes below an hour, hours and padded minutes above', () => {
    expect(tripTime(7.4)).toBe('7 min');
    expect(tripTime(59.6)).toBe('1 h 00');
    expect(tripTime(105)).toBe('1 h 45');
  });

  it('spans off-peak to rush hour, once when they match', () => {
    expect(tripRange(49, 105)).toBe('≈ 49 min – 1 h 45');
    expect(tripRange(20, 20.2)).toBe('≈ 20 min');
  });
});

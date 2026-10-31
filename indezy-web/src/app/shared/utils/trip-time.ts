/** A trip's time as minutes, or hours and minutes ("1 h 05"). */
export function tripTime(minutes: number): string {
  const rounded = Math.round(minutes);
  return rounded < 60 ? `${rounded} min` : `${Math.floor(rounded / 60)} h ${String(rounded % 60).padStart(2, '0')}`;
}

/** An estimated trip from off-peak to rush hour ("≈ 50 min – 1 h 45"), one time when both match. */
export function tripRange(minutes: number, rushMinutes: number): string {
  return Math.round(minutes) === Math.round(rushMinutes) ? `≈ ${tripTime(minutes)}` : `≈ ${tripTime(minutes)} – ${tripTime(rushMinutes)}`;
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Season } from '../../models/season.models';

/** Selection value for "every opportunity, whatever its season". */
export const ALL_SEASONS = 'all';
export type SeasonSelection = number | typeof ALL_SEASONS;

@Injectable({
  providedIn: 'root'
})
export class SeasonService {
  /** Remembers the season last opened on the dashboard. */
  static readonly SELECTION_KEY = 'indezy-season';

  private readonly API_URL = `${environment.apiUrl}/seasons`;

  constructor(private readonly http: HttpClient) {}

  /** Seasons of a workspace, most recent first. */
  getByFreelanceId(freelanceId: number): Observable<Season[]> {
    return this.http.get<Season[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  create(season: Season): Observable<Season> {
    return this.http.post<Season>(this.API_URL, season);
  }

  update(id: number, season: Season): Observable<Season> {
    return this.http.put<Season>(`${this.API_URL}/${id}`, season);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  /**
   * The season to open: the remembered one when it still exists, otherwise the running season,
   * otherwise every season.
   */
  static pickInitialSelection(seasons: Season[], saved: string | null): SeasonSelection {
    if (saved === ALL_SEASONS) {
      return ALL_SEASONS;
    }
    const savedId = saved ? Number(saved) : NaN;
    if (seasons.some(season => season.id === savedId)) {
      return savedId;
    }
    return seasons.find(season => season.active)?.id ?? ALL_SEASONS;
  }

  static readSavedSelection(): string | null {
    try {
      return localStorage.getItem(SeasonService.SELECTION_KEY);
    } catch {
      return null;
    }
  }

  static saveSelection(selection: SeasonSelection): void {
    try {
      localStorage.setItem(SeasonService.SELECTION_KEY, String(selection));
    } catch {
      // Storage can be unavailable (private mode); the choice then lasts for this visit only.
    }
  }
}

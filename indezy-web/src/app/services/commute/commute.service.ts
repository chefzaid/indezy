import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CommuteInfoDto, OpportunityMap, ProjectCommuteDto, TravelMode } from '../../models/commute.models';

@Injectable({
  providedIn: 'root'
})
export class CommuteService {
  private readonly API_URL = `${environment.apiUrl}/commute`;

  constructor(private readonly http: HttpClient) {}

  getProjectsSortedByCommute(freelanceId: number, travelMode: TravelMode = 'DRIVING'): Observable<ProjectCommuteDto[]> {
    const params = new HttpParams().set('travelMode', travelMode);
    return this.http.get<ProjectCommuteDto[]>(`${this.API_URL}/projects/${freelanceId}`, { params });
  }

  /** The year's professional kilometres as CSV: days on site of the validated reports times each round trip by car. */
  exportKilometres(freelanceId: number, year: number, language: 'FR' | 'EN'): Observable<Blob> {
    return this.http.get(`${this.API_URL}/kilometres/${freelanceId}/${year}`, { params: { language }, responseType: 'blob' });
  }

  /** The home address and the opportunities at their places, for the map. */
  getMap(freelanceId: number): Observable<OpportunityMap> {
    return this.http.get<OpportunityMap>(`${this.API_URL}/map/${freelanceId}`);
  }

  /** One opportunity's trip by car, then by public transport. */
  getProjectCommute(projectId: number): Observable<CommuteInfoDto[]> {
    return this.http.get<CommuteInfoDto[]>(`${this.API_URL}/project/${projectId}`);
  }

}

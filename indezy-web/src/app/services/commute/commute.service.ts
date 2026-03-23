import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CommuteInfoDto, ProjectCommuteDto, TravelMode } from '../../models/commute.models';

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

  getCommuteForProject(freelanceId: number, projectId: number, travelMode: TravelMode = 'DRIVING'): Observable<CommuteInfoDto> {
    const params = new HttpParams().set('travelMode', travelMode);
    return this.http.get<CommuteInfoDto>(`${this.API_URL}/projects/${freelanceId}/${projectId}`, { params });
  }
}

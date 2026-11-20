import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { IdeaStatus, NetworkIdea, NetworkIdeas } from '../../models';

/** How the ideas board is ordered: the most voted first, or the newest. */
export type IdeaOrder = 'TOP' | 'NEW';

/** The member network's ideas board: ideas for Indezy proposed, voted for, and answered by the administrators. */
@Injectable({ providedIn: 'root' })
export class NetworkIdeasService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/network/ideas`;

  list(sort: IdeaOrder, status: IdeaStatus | null, typed = '', page = 0): Observable<NetworkIdeas> {
    let params = new HttpParams().set('sort', sort).set('q', typed).set('page', page);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<NetworkIdeas>(this.url, { params });
  }

  get(id: number): Observable<NetworkIdea> {
    return this.http.get<NetworkIdea>(`${this.url}/${id}`);
  }

  create(title: string, description: string | null): Observable<NetworkIdea> {
    return this.http.post<NetworkIdea>(this.url, { title, description });
  }

  update(id: number, title: string, description: string | null): Observable<NetworkIdea> {
    return this.http.put<NetworkIdea>(`${this.url}/${id}`, { title, description });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Votes for an idea, or takes the vote back. */
  vote(id: number, on: boolean): Observable<NetworkIdea> {
    return on ? this.http.put<NetworkIdea>(`${this.url}/${id}/vote`, null) : this.http.delete<NetworkIdea>(`${this.url}/${id}/vote`);
  }

  /** The administrators' answer: a status and a word. */
  setStatus(id: number, status: IdeaStatus, note: string | null): Observable<NetworkIdea> {
    return this.http.put<NetworkIdea>(`${this.url}/${id}/status`, { status, note });
  }

  /** Merges a duplicate into the idea it repeats, answering the idea kept. */
  merge(id: number, into: number): Observable<NetworkIdea> {
    return this.http.put<NetworkIdea>(`${this.url}/${id}/merge`, null, { params: new HttpParams().set('into', into) });
  }
}

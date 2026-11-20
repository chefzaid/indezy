import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Community, CommunityMember, CommunityPage, MemberCard, NetworkPost, NetworkPosts } from '../../models';

/** The member network's communities: listed, created, joined, read and moderated. */
@Injectable({ providedIn: 'root' })
export class NetworkCommunitiesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/network/communities`;

  list(typed = ''): Observable<Community[]> {
    return this.http.get<Community[]>(this.url, { params: new HttpParams().set('q', typed) });
  }

  create(name: string, description: string | null, privateCommunity = false): Observable<Community> {
    return this.http.post<Community>(this.url, { name, description, privateCommunity });
  }

  page(slug: string): Observable<CommunityPage> {
    return this.http.get<CommunityPage>(`${this.url}/${encodeURIComponent(slug)}`);
  }

  update(slug: string, name: string, description: string | null, privateCommunity: boolean): Observable<CommunityPage> {
    return this.http.put<CommunityPage>(`${this.url}/${encodeURIComponent(slug)}`, { name, description, privateCommunity });
  }

  /** The members asking to join a private community, for its moderators. */
  requests(slug: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.url}/${encodeURIComponent(slug)}/requests`);
  }

  /** Lets a member in, or declines. */
  answer(slug: string, handle: string, accepted: boolean): Observable<MemberCard[]> {
    const path = `${this.url}/${encodeURIComponent(slug)}/requests/${encodeURIComponent(handle)}`;
    return accepted ? this.http.put<MemberCard[]>(path, null) : this.http.delete<MemberCard[]>(path);
  }

  remove(slug: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(slug)}`);
  }

  join(slug: string): Observable<CommunityPage> {
    return this.http.put<CommunityPage>(`${this.url}/${encodeURIComponent(slug)}/membership`, null);
  }

  leave(slug: string): Observable<CommunityPage> {
    return this.http.delete<CommunityPage>(`${this.url}/${encodeURIComponent(slug)}/membership`);
  }

  posts(slug: string, page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.url}/${encodeURIComponent(slug)}/posts`, { params: { page } });
  }

  /** Posts in the community; none when the spam score holds it for review. */
  post(slug: string, text: string): Observable<NetworkPost | null> {
    return this.http.post<NetworkPost | null>(`${this.url}/${encodeURIComponent(slug)}/posts`, { text });
  }

  removePost(slug: string, postId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(slug)}/posts/${postId}`);
  }

  members(slug: string): Observable<CommunityMember[]> {
    return this.http.get<CommunityMember[]>(`${this.url}/${encodeURIComponent(slug)}/members`);
  }

  removeMember(slug: string, handle: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(slug)}/members/${encodeURIComponent(handle)}`);
  }

  setModerator(slug: string, handle: string, moderator: boolean): Observable<CommunityMember[]> {
    const path = `${this.url}/${encodeURIComponent(slug)}/moderators/${encodeURIComponent(handle)}`;
    return moderator ? this.http.put<CommunityMember[]>(path, null) : this.http.delete<CommunityMember[]>(path);
  }
}

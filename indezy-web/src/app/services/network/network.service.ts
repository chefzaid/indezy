import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DirectorySearch, MemberCard, MemberDirectory, MemberPage, MemberSuggestion, NetworkComment, NetworkPost, NetworkPostVersion, NetworkPosts, SavedPosts, TagPage, NetworkAvailabilitySetting, NetworkFields, NetworkMembership, NetworkRestrictions } from '../../models';

/** The member network: the account's membership, handles and the members' cards. */
@Injectable({
  providedIn: 'root'
})
export class NetworkService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network`;

  membership(): Observable<NetworkMembership> {
    return this.http.get<NetworkMembership>(`${this.API_URL}/membership`);
  }

  /** Joins, or changes the handle and the card. */
  join(handle: string, fields: NetworkFields, availability: NetworkAvailabilitySetting): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership`, { handle, fields, availability });
  }

  /** Verifies the member against the public company registry, from the billing SIRET. */
  verify(): Observable<NetworkMembership> {
    return this.http.post<NetworkMembership>(`${this.API_URL}/membership/verification`, null);
  }

  leave(): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/membership`);
  }

  handle(handle: string): Observable<{ handle: string; available: boolean }> {
    return this.http.get<{ handle: string; available: boolean }>(`${this.API_URL}/handles/${encodeURIComponent(handle)}`);
  }

  post(text: string): Observable<NetworkPost> {
    return this.http.post<NetworkPost>(`${this.API_URL}/posts`, { text });
  }

  /** Changes the text of the reader's own post, in the 15 minutes after posting it. */
  editPost(id: number, text: string): Observable<NetworkPost> {
    return this.http.put<NetworkPost>(`${this.API_URL}/posts/${id}`, { text });
  }

  /** Saves a post to the reader's private list, or takes it off. */
  bookmark(id: number, saved: boolean): Observable<NetworkPost> {
    const url = `${this.API_URL}/posts/${id}/bookmark`;
    return saved ? this.http.put<NetworkPost>(url, null) : this.http.delete<NetworkPost>(url);
  }

  /** The reader's saved posts, 20 a page, those naming the tag when one is given. */
  saved(tag: string | null, page = 0): Observable<SavedPosts> {
    const params: Record<string, string | number> = tag ? { tag, page } : { page };
    return this.http.get<SavedPosts>(`${this.API_URL}/bookmarks`, { params });
  }

  postVersions(id: number): Observable<NetworkPostVersion[]> {
    return this.http.get<NetworkPostVersion[]>(`${this.API_URL}/posts/${id}/versions`);
  }

  deletePost(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/posts/${id}`);
  }

  /** Likes a post, or no longer; answers the post as the reader now reads it. */
  like(id: number, liked: boolean): Observable<NetworkPost> {
    const url = `${this.API_URL}/posts/${id}/like`;
    return liked ? this.http.put<NetworkPost>(url, null) : this.http.delete<NetworkPost>(url);
  }

  comments(postId: number): Observable<NetworkComment[]> {
    return this.http.get<NetworkComment[]>(`${this.API_URL}/posts/${postId}/comments`);
  }

  /** Comments a post, or replies to one of its comments. */
  comment(postId: number, text: string, parentId: number | null = null): Observable<NetworkComment> {
    return this.http.post<NetworkComment>(`${this.API_URL}/posts/${postId}/comments`, { text, parentId });
  }

  deleteComment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/comments/${id}`);
  }

  hideComment(id: number, hidden: boolean): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/comments/${id}/hidden`, null, { params: { hidden } });
  }

  closeComments(postId: number, closed: boolean): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/posts/${postId}/comments-closed`, null, { params: { closed } });
  }

  /** Pins a comment above the others, or none. */
  pinComment(postId: number, commentId: number | null): Observable<void> {
    const params: Record<string, number> = commentId === null ? {} : { commentId };
    return this.http.put<void>(`${this.API_URL}/posts/${postId}/pinned-comment`, null, { params });
  }

  likers(id: number): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/posts/${id}/likes`);
  }

  /** The reader's posts and those of the members they follow, 20 a page. */
  feed(page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.API_URL}/feed`, { params: { page } });
  }

  memberPosts(handle: string, page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.member$(handle)}/posts`, { params: { page } });
  }

  tagPage(tag: string, page = 0): Observable<TagPage> {
    return this.http.get<TagPage>(`${this.API_URL}/tags/${encodeURIComponent(tag)}`, { params: { page } });
  }

  followedTags(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/tags`);
  }

  /** Follows a tag, or no longer; answers the tags the reader follows. */
  followTag(tag: string, followed: boolean): Observable<string[]> {
    const url = `${this.API_URL}/tags/${encodeURIComponent(tag)}/follow`;
    return followed ? this.http.put<string[]>(url, null) : this.http.delete<string[]>(url);
  }

  /** The members found by a skill, a city, an availability or remote work, 20 a page. */
  directory(search: DirectorySearch): Observable<MemberDirectory> {
    let params = new HttpParams().set('page', search.page ?? 0);
    for (const key of ['skill', 'city', 'availability', 'remote'] as const) {
      const value = search[key];
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value).trim());
      }
    }
    return this.http.get<MemberDirectory>(`${this.API_URL}/members`, { params });
  }

  /** Members to follow, each with its reason. */
  suggestions(): Observable<MemberSuggestion[]> {
    return this.http.get<MemberSuggestion[]>(`${this.API_URL}/suggestions`);
  }

  member(handle: string): Observable<MemberCard> {
    return this.http.get<MemberCard>(`${this.member$(handle)}`);
  }

  page(handle: string): Observable<MemberPage> {
    return this.http.get<MemberPage>(`${this.member$(handle)}/page`);
  }

  follow(handle: string): Observable<MemberPage> {
    return this.http.put<MemberPage>(`${this.member$(handle)}/follow`, null);
  }

  unfollow(handle: string): Observable<MemberPage> {
    return this.http.delete<MemberPage>(`${this.member$(handle)}/follow`);
  }

  followers(handle: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.member$(handle)}/followers`);
  }

  following(handle: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.member$(handle)}/following`);
  }

  /** Mutes a member, or no longer. */
  mute(handle: string, muted: boolean): Observable<MemberPage> {
    const url = `${this.member$(handle)}/mute`;
    return muted ? this.http.put<MemberPage>(url, null) : this.http.delete<MemberPage>(url);
  }

  /** Blocks a member, or no longer. */
  block(handle: string, blocked: boolean): Observable<MemberPage> {
    const url = `${this.member$(handle)}/block`;
    return blocked ? this.http.put<MemberPage>(url, null) : this.http.delete<MemberPage>(url);
  }

  restrictions(): Observable<NetworkRestrictions> {
    return this.http.get<NetworkRestrictions>(`${this.API_URL}/restrictions`);
  }

  /** Stops a member following the viewer. */
  removeFollower(handle: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/followers/${encodeURIComponent(handle)}`);
  }

  private member$(handle: string): string {
    return `${this.API_URL}/members/${encodeURIComponent(handle)}`;
  }
}

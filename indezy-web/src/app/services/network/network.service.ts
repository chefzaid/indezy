import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DirectorySearch, MemberCard, MemberDirectory, MemberPage, MemberSuggestion, TrendingTag, NetworkDraft, NetworkNotificationType, NotificationGroup, NetworkComment, NetworkPost, NetworkPostVersion, NetworkPosts, SavedPosts, TagPage, NetworkAvailabilitySetting, NetworkFields, NetworkMembership, NetworkRestrictions, ReportReason, ReportTarget, HeldForReview, ConfidentialMatch, NewPoll, PostDetails, PostKind, ProjectDto } from '../../models';

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

  /** Posts the text, of a kind with its facts; the spam score may hold it for review. */
  post(text: string, kind: PostKind = 'POST', details: PostDetails | null = null, poll: NewPoll | null = null):
    Observable<NetworkPost | HeldForReview> {
    return this.http.post<NetworkPost | HeldForReview>(`${this.API_URL}/posts`, { text, kind, details, poll });
  }

  /** Adds a mission passed on to the reader's pipeline; named lets its author see who took it. */
  take(postId: number, named: boolean): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(`${this.API_URL}/posts/${postId}/pipeline`, null, { params: new HttpParams().set('named', named) });
  }

  /** The members who took the reader's own mission and let them know. */
  takers(postId: number): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/posts/${postId}/takers`);
  }

  /** Finds a post useful, or no longer: the second reaction, for knowledge posts. */
  useful(postId: number, useful: boolean): Observable<NetworkPost> {
    const url = `${this.API_URL}/posts/${postId}/useful`;
    return useful ? this.http.put<NetworkPost>(url, null) : this.http.delete<NetworkPost>(url);
  }

  /** Votes in a poll, or changes the vote, while it is open. */
  vote(postId: number, choice: number): Observable<NetworkPost> {
    return this.http.put<NetworkPost>(`${this.API_URL}/posts/${postId}/vote`, null, { params: new HttpParams().set('choice', choice) });
  }

  /** One post, as the reader may read it. */
  onePost(id: number): Observable<NetworkPost> {
    return this.http.get<NetworkPost>(`${this.API_URL}/posts/${id}`);
  }

  /** Picks a comment under the reader's own question as its answer, or none. */
  answer(postId: number, commentId: number | null): Observable<void> {
    const params = commentId === null ? new HttpParams() : new HttpParams().set('commentId', commentId);
    return this.http.put<void>(`${this.API_URL}/posts/${postId}/answer`, null, { params });
  }

  /** Changes the text of the reader's own post, in the 15 minutes after posting it. */
  editPost(id: number, text: string): Observable<NetworkPost> {
    return this.http.put<NetworkPost>(`${this.API_URL}/posts/${id}`, { text });
  }

  drafts(): Observable<NetworkDraft[]> {
    return this.http.get<NetworkDraft[]>(`${this.API_URL}/drafts`);
  }

  /** Keeps a draft, scheduled when a time is given. */
  createDraft(text: string, scheduledAt: string | null): Observable<NetworkDraft> {
    return this.http.post<NetworkDraft>(`${this.API_URL}/drafts`, { text, scheduledAt });
  }

  updateDraft(id: number, text: string, scheduledAt: string | null): Observable<NetworkDraft> {
    return this.http.put<NetworkDraft>(`${this.API_URL}/drafts/${id}`, { text, scheduledAt });
  }

  deleteDraft(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/drafts/${id}`);
  }

  publishDraft(id: number): Observable<NetworkPost | HeldForReview> {
    return this.http.post<NetworkPost | HeldForReview>(`${this.API_URL}/drafts/${id}/publish`, null);
  }

  /** Reposts a post, with an optional comment. */
  repost(id: number, text: string): Observable<NetworkPost> {
    return this.http.post<NetworkPost>(`${this.API_URL}/posts/${id}/reposts`, { text });
  }

  /** Turns reposts of the reader's own post off, or on again. */
  repostsOff(id: number, off: boolean): Observable<NetworkPost> {
    return this.http.put<NetworkPost>(`${this.API_URL}/posts/${id}/reposts-off`, null, { params: { off } });
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

  /** Comments a post, or replies to one of its comments; the spam score may hold it for review. */
  comment(postId: number, text: string, parentId: number | null = null): Observable<NetworkComment | HeldForReview> {
    return this.http.post<NetworkComment | HeldForReview>(`${this.API_URL}/posts/${postId}/comments`, { text, parentId });
  }

  likeComment(id: number, liked: boolean): Observable<NetworkComment> {
    const url = `${this.API_URL}/comments/${id}/like`;
    return liked ? this.http.put<NetworkComment>(url, null) : this.http.delete<NetworkComment>(url);
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
  /** How many posts by others reached the home feed after the given time (a post's `createdAt`). */
  newerCount(since: string): Observable<number> {
    return this.http.get<{ count: number }>(`${this.API_URL}/feed/newer`, { params: { since } }).pipe(map(found => found.count));
  }

  /** The tags most named in the last seven days by the posts the reader can read. */
  trendingTags(): Observable<TrendingTag[]> {
    return this.http.get<TrendingTag[]>(`${this.API_URL}/tags/trending`);
  }

  /** The tags in use starting with what was typed, the most used first. */
  suggestTags(typed: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/tags/suggest`, { params: { q: typed } });
  }

  /** For you: the recent posts of every member the reader can read, ranked by what they share with the reader. */
  forYou(page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.API_URL}/feed/for-you`, { params: { page } });
  }


  memberPosts(handle: string, page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.member$(handle)}/posts`, { params: { page } });
  }

  /** A tag's page, its posts the newest first or the most found useful first. */
  tagPage(tag: string, page = 0, sort: 'recent' | 'useful' = 'recent'): Observable<TagPage> {
    return this.http.get<TagPage>(`${this.API_URL}/tags/${encodeURIComponent(tag)}`, { params: { page, sort } });
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

  notifications(): Observable<NotificationGroup[]> {
    return this.http.get<NotificationGroup[]>(`${this.API_URL}/notifications`);
  }

  unreadNotifications(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>(`${this.API_URL}/notifications/unread`);
  }

  readNotifications(): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/notifications/read`, null);
  }

  /** The kinds of notification the reader turned off. */
  notificationSettings(): Observable<{ off: NetworkNotificationType[] }> {
    return this.http.get<{ off: NetworkNotificationType[] }>(`${this.API_URL}/notifications/settings`);
  }

  saveNotificationSettings(off: NetworkNotificationType[]): Observable<{ off: NetworkNotificationType[] }> {
    return this.http.put<{ off: NetworkNotificationType[] }>(`${this.API_URL}/notifications/settings`, { off });
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

  /** Reports a post, a comment (by id) or a member (by handle) to the moderators. */
  report(target: ReportTarget, id: number | string, reason: ReportReason, details: string | null): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/${target}/${encodeURIComponent(String(id))}/report`, { reason, details });
  }

  /** The workspace's client, ESN and contact names found in a text before it goes out. */
  checkConfidentiality(text: string): Observable<ConfidentialMatch[]> {
    return this.http.post<ConfidentialMatch[]>(`${this.API_URL}/confidentiality`, { text });
  }

  /** Turns the member's card private, or public again, approving every request waiting. */
  setPrivate(privateCard: boolean): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership/private`, null, { params: new HttpParams().set('private', privateCard) });
  }

  /** The members asking to follow the member. */
  followRequests(): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/follow-requests`);
  }

  /** Approves a member's request to follow, or declines it; answers the requests left. */
  answerFollowRequest(handle: string, approve: boolean): Observable<MemberCard[]> {
    const url = `${this.API_URL}/follow-requests/${encodeURIComponent(handle)}`;
    return approve ? this.http.put<MemberCard[]>(url, null) : this.http.delete<MemberCard[]>(url);
  }

  private member$(handle: string): string {
    return `${this.API_URL}/members/${encodeURIComponent(handle)}`;
  }
}

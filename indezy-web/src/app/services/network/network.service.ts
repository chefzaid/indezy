import { OfferMatch } from '../../models/offer.models';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';
import { ShownTestimonial } from '../../models/testimonial.models';
import {
  ConfidentialMatch, DirectorySearch, FileRef, HeldForReview, MemberCard, MemberDirectory, MemberPage, MemberSuggestion,
  NetworkAvailabilitySetting, NetworkComment, NetworkDraft, NetworkFields, NetworkMembership, NetworkNotificationType, NetworkPost,
  NetworkPostVersion, NetworkPosts, PageViews, PostStats, AuthorActivity, MutualFollows, MemberAbout, NetworkAbout, MemberSearchFilters, PostSearchFilters, MissionFilters, NetworkRestrictions, NewPoll, NotificationGroup, PostDetails, PostKind, ProjectDto, Reaction,
  ReportReason, ReportTarget, SavedPosts, TagPage, TrendingTag
} from '../../models';

/** The member network: the account's membership, handles and the members' cards. */
/** The kinds of notification turned off, and those also emailed. */
export interface NoticeSettings {
  off: NetworkNotificationType[];
  emailed: NetworkNotificationType[];
}

@Injectable({
  providedIn: 'root'
})
export class NetworkService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network`;

  /** The account's membership; silent, its failure told by the caller, when read in the background (the network's tabs). */
  membership(silent = false): Observable<NetworkMembership> {
    return this.http.get<NetworkMembership>(`${this.API_URL}/membership`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
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
  post(text: string, kind: PostKind = 'POST', details: PostDetails | null = null, poll: NewPoll | null = null, files: FileRef[] = []):
    Observable<NetworkPost | HeldForReview> {
    return this.http.post<NetworkPost | HeldForReview>(`${this.API_URL}/posts`, files.length ? { text, kind, details, poll, files }
      : { text, kind, details, poll });
  }

  /** Adds a mission passed on to the reader's pipeline; named lets its author see who took it. */
  take(postId: number, named: boolean): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(`${this.API_URL}/posts/${postId}/pipeline`, null, { params: new HttpParams().set('named', named) });
  }

  /** The members who took the reader's own mission and let them know. */
  takers(postId: number): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/posts/${postId}/takers`);
  }

  /** Votes in a poll, or changes the vote, while it is open. */
  vote(postId: number, choice: number): Observable<NetworkPost> {
    return this.http.put<NetworkPost>(`${this.API_URL}/posts/${postId}/vote`, null, { params: new HttpParams().set('choice', choice) });
  }

  /** One post, as the reader may read it. */
  /** **Not interested** in a post of For you: hidden there, counting against its author and tags; or interested again. */
  notInterested(id: number, dismissed: boolean): Observable<void> {
    const url = `${this.API_URL}/posts/${id}/not-interested`;
    return dismissed ? this.http.put<void>(url, null) : this.http.delete<void>(url);
  }

  /** Pins one of the reader's posts at the top of their page (replacing the one pinned), or unpins it. */
  pinPost(id: number, pinned: boolean): Observable<void> {
    return pinned ? this.http.put<void>(`${this.API_URL}/posts/${id}/pin`, null) : this.http.delete<void>(`${this.API_URL}/posts/${id}/pin`);
  }

  onePost(id: number, silent = false): Observable<NetworkPost> {
    return this.http.get<NetworkPost>(`${this.API_URL}/posts/${id}`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
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
  /** Reacts to a post (a like by default), or takes the reaction back (null). */
  react(id: number, reaction: Reaction | null): Observable<NetworkPost> {
    const url = `${this.API_URL}/posts/${id}/like`;
    return reaction ? this.http.put<NetworkPost>(url, null, { params: { reaction } }) : this.http.delete<NetworkPost>(url);
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
  trendingTags(silent = false): Observable<TrendingTag[]> {
    return this.http.get<TrendingTag[]>(`${this.API_URL}/tags/trending`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
  }

  /** Members to mention for what was typed after an @, those followed first. */
  mentions(typed: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/mentions`, { params: { q: typed } });
  }

  /** The tags in use starting with what was typed, the most used first. */
  suggestTags(typed: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/tags/suggest`, { params: { q: typed } });
  }

  /** For you: the recent posts of every member the reader can read, ranked by what they share with the reader. */
  /** The missions members pass on, from every member the reader can read but themselves, the newest first. */
  missions(page = 0, filters: MissionFilters = {}, silent = false): Observable<NetworkPosts> {
    let params = new HttpParams().set('page', page);
    for (const key of ['skill', 'city', 'remote', 'rate', 'before'] as const) {
      const value = filters[key];
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value).trim());
      }
    }
    if (filters.hideTaken) {
      params = params.set('hideTaken', true);
    }
    if (filters.bestMatch) {
      params = params.set('bestMatch', true);
    }
    return this.http.get<NetworkPosts>(`${this.API_URL}/feed/missions`, { params, context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
  }

  /** How the missions of these ids fit the reader's profile, by post id; empty without profile skills. */
  missionMatches(ids: number[]): Observable<Record<number, OfferMatch>> {
    return this.http.get<Record<number, OfferMatch>>(`${this.API_URL}/feed/missions/matches`, { params: { ids: ids.join(',') } });
  }

  forYou(page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.API_URL}/feed/for-you`, { params: { page } });
  }


  memberPosts(handle: string, page = 0): Observable<NetworkPosts> {
    return this.http.get<NetworkPosts>(`${this.member$(handle)}/posts`, { params: { page } });
  }

  /** A tag's page, its posts the newest first or the most liked first. */
  tagPage(tag: string, page = 0, sort: 'recent' | 'liked' = 'recent'): Observable<TagPage> {
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
  /** A member's About section and the missions they list. */
  memberAbout(handle: string): Observable<MemberAbout> {
    return this.http.get<MemberAbout>(`${this.API_URL}/members/${encodeURIComponent(handle)}/about`,
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** The reader's own About section, and whether their won missions show. */
  myAbout(): Observable<NetworkAbout> {
    return this.http.get<NetworkAbout>(`${this.API_URL}/membership/about`);
  }

  saveAbout(about: NetworkAbout): Observable<NetworkAbout> {
    return this.http.put<NetworkAbout>(`${this.API_URL}/membership/about`, about);
  }

  /** The members the reader follows who follow the member too. */
  mutual(handle: string): Observable<MutualFollows> {
    return this.http.get<MutualFollows>(`${this.API_URL}/members/${encodeURIComponent(handle)}/mutual`,
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** A post's analytics, for its author. */
  postStats(id: number): Observable<PostStats> {
    return this.http.get<PostStats>(`${this.API_URL}/posts/${id}/stats`);
  }

  /** The reader's activity on the network. */
  activity(silent = false): Observable<AuthorActivity> {
    return this.http.get<AuthorActivity>(`${this.API_URL}/activity`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
  }

  /** **Open to work** in one click, or back to the availability the won missions tell. */
  setOpenToWork(on: boolean): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership/open-to-work`, null, { params: { on } });
  }

  /** The reader opened a member's page (kept once a day; nothing for their own), in the background. */
  recordView(handle: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/members/${encodeURIComponent(handle)}/views`, null,
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** Who viewed the reader's page in the last 90 days. */
  pageViews(silent = false): Observable<PageViews> {
    return this.http.get<PageViews>(`${this.API_URL}/page-views`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
  }

  /** Browses other members' pages privately, or no longer. */
  setPrivateViewing(on: boolean): Observable<PageViews> {
    return this.http.put<PageViews>(`${this.API_URL}/page-views/private`, null, { params: { on } });
  }

  /** A member's recommendations: the testimonials they approved, the latest first (none for a private card not followed). */
  recommendations(handle: string): Observable<ShownTestimonial[]> {
    return this.http.get<ShownTestimonial[]>(`${this.API_URL}/members/${encodeURIComponent(handle)}/recommendations`,
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** The network's search for members: every word typed names them (name, handle, skill, city or headline), 20 a page. */
  searchMembers(typed: string, page = 0, filters: MemberSearchFilters = {}): Observable<MemberDirectory> {
    let params = new HttpParams().set('q', typed).set('page', page);
    if (filters.city) {
      params = params.set('city', filters.city);
    }
    if (filters.availability) {
      params = params.set('availability', filters.availability);
    }
    if (filters.remote) {
      params = params.set('remote', true);
    }
    if (filters.now) {
      params = params.set('now', true);
    }
    return this.http.get<MemberDirectory>(`${this.API_URL}/search/members`, { params });
  }

  /** The network's search for posts: those holding what was typed, case aside, the newest first, 20 a page. */
  searchPosts(typed: string, page = 0, filters: PostSearchFilters = {}): Observable<NetworkPosts> {
    let params = new HttpParams().set('q', typed).set('page', page);
    (filters.kinds ?? []).forEach(kind => params = params.append('kind', kind));
    if (filters.days) {
      params = params.set('days', filters.days);
    }
    if (filters.followed) {
      params = params.set('followed', true);
    }
    return this.http.get<NetworkPosts>(`${this.API_URL}/search/posts`, { params });
  }

  directory(search: DirectorySearch): Observable<MemberDirectory> {
    let params = new HttpParams().set('page', search.page ?? 0);
    for (const key of ['skill', 'city', 'availability', 'remote'] as const) {
      const value = search[key];
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value).trim());
      }
    }
    if (search.now) {
      params = params.set('now', true);
    }
    return this.http.get<MemberDirectory>(`${this.API_URL}/members`, { params });
  }

  notifications(): Observable<NotificationGroup[]> {
    return this.http.get<NotificationGroup[]>(`${this.API_URL}/notifications`);
  }

  unreadNotifications(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>(`${this.API_URL}/notifications/unread`);
  }

  /** Deletes a group of the reader's notifications: those of a kind about a post, or about none. */
  dismissNotification(type: NetworkNotificationType, postId: number | null): Observable<void> {
    let params = new HttpParams().set('type', type);
    if (postId !== null) {
      params = params.set('postId', postId);
    }
    return this.http.delete<void>(`${this.API_URL}/notifications`, { params });
  }

  readNotifications(): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/notifications/read`, null);
  }

  /** The kinds of notification the reader turned off. */
  /** The kinds of notification turned off, and those also emailed. */
  notificationSettings(): Observable<NoticeSettings> {
    return this.http.get<NoticeSettings>(`${this.API_URL}/notifications/settings`);
  }

  /** Turns kinds off, and chooses those also emailed (left as they are when none are given). */
  saveNotificationSettings(off: NetworkNotificationType[], emailed?: NetworkNotificationType[]): Observable<NoticeSettings> {
    return this.http.put<NoticeSettings>(`${this.API_URL}/notifications/settings`, emailed === undefined ? { off } : { off, emailed });
  }

  /** Members to follow, each with its reason. */
  suggestions(silent = false): Observable<MemberSuggestion[]> {
    return this.http.get<MemberSuggestion[]>(`${this.API_URL}/suggestions`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
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
  /** Suggests members who went through the same ESNs, or no longer; only between members who both allow it. */
  setEsnSuggestions(on: boolean): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership/esn-suggestions`, null, { params: new HttpParams().set('on', on) });
  }

  /** Suggestions by workspace contacts (matched on the sign-in address), both ways. */
  setContactSuggestions(on: boolean): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership/contact-suggestions`, null, { params: new HttpParams().set('on', on) });
  }

  setPrivate(privateCard: boolean): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership/private`, null, { params: new HttpParams().set('private', privateCard) });
  }

  /** The members asking to follow the member; silent, its failure told by the caller, when only counted in the background. */
  followRequests(silent = false): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/follow-requests`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, silent) });
  }

  /** The members the member asked to follow, waiting for their answer, the latest asked first. */
  sentFollowRequests(): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.API_URL}/follow-requests/sent`);
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

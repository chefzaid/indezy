import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Conversation, FoundMessage, Message, MessageThread, MessagingSettings, ProjectDto, Reaction } from '../../models';

/** Conversations between members and their messages. */
@Injectable({
  providedIn: 'root'
})
export class NetworkMessagesService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network`;

  conversations(archived = false): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(`${this.API_URL}/conversations`, { params: { archived } });
  }

  /** The conversation with a member: the one open, or a new one when they follow the reader (409 with the reason otherwise). */
  open(handle: string): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.API_URL}/conversations`, { handle });
  }

  /** Creates a group of members who all follow the reader. */
  createGroup(title: string, handles: string[]): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.API_URL}/groups`, { title, handles });
  }

  addMembers(id: number, handles: string[]): Observable<Conversation> {
    return this.http.post<Conversation>(`${this.API_URL}/groups/${id}/members`, { handles });
  }

  renameGroup(id: number, title: string): Observable<Conversation> {
    return this.http.put<Conversation>(`${this.API_URL}/groups/${id}/title`, { title });
  }

  leaveGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/groups/${id}/members/me`);
  }

  /** The reader's messages holding what was typed, the latest first. */
  search(typed: string): Observable<FoundMessage[]> {
    return this.http.get<FoundMessage[]>(`${this.API_URL}/conversations/search`, { params: { q: typed } });
  }

  /** How many conversations have unread messages, the muted aside. */
  unread(): Observable<number> {
    return this.http.get<{ conversations: number }>(`${this.API_URL}/conversations/unread`).pipe(map(found => found.conversations));
  }

  /** The conversation with its latest messages, or those before one already shown. */
  thread(id: number, before: number | null = null): Observable<MessageThread> {
    return this.http.get<MessageThread>(`${this.API_URL}/conversations/${id}`, before === null ? {} : { params: { before } });
  }

  /** The whole conversation as a PDF, in the language asked. */
  pdf(id: number, language: 'FR' | 'EN'): Observable<Blob> {
    return this.http.get(`${this.API_URL}/conversations/${id}/pdf`, { params: { language }, responseType: 'blob' });
  }

  read(id: number): Observable<Conversation> {
    return this.http.put<Conversation>(`${this.API_URL}/conversations/${id}/read`, null);
  }

  /** Tells the conversation's other members the reader is typing. */
  typing(id: number): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/conversations/${id}/typing`, null);
  }

  /** Whether the member shows and sees read receipts and who is typing. */
  messaging(): Observable<MessagingSettings> {
    return this.http.get<MessagingSettings>(`${this.API_URL}/messages/settings`);
  }

  saveMessaging(settings: Partial<MessagingSettings>): Observable<MessagingSettings> {
    return this.http.put<MessagingSettings>(`${this.API_URL}/messages/settings`, settings);
  }

  settings(id: number, settings: { muted?: boolean; archived?: boolean }): Observable<Conversation> {
    return this.http.put<Conversation>(`${this.API_URL}/conversations/${id}/settings`, settings);
  }

  /** Sends a message: its text, the message it replies to, and one of the reader's opportunities shared as a card. */
  send(id: number, text: string, replyTo: number | null = null, opportunityId: number | null = null): Observable<Message> {
    return this.http.post<Message>(`${this.API_URL}/conversations/${id}/messages`, { text, replyTo, opportunityId });
  }

  /** Adds the opportunity a message shares to the reader's pipeline. */
  take(messageId: number): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(`${this.API_URL}/messages/${messageId}/pipeline`, null);
  }

  edit(id: number, text: string): Observable<Message> {
    return this.http.put<Message>(`${this.API_URL}/messages/${id}`, { text });
  }

  remove(id: number): Observable<Message> {
    return this.http.delete<Message>(`${this.API_URL}/messages/${id}`);
  }

  /** Reacts to a message, or takes the reaction back (null). */
  react(id: number, reaction: Reaction | null): Observable<Message> {
    const url = `${this.API_URL}/messages/${id}/reaction`;
    return reaction ? this.http.put<Message>(url, null, { params: { reaction } }) : this.http.delete<Message>(url);
  }
}

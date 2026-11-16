import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Conversation, Message, MessageThread, Reaction } from '../../models';

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

  /** How many conversations have unread messages, the muted aside. */
  unread(): Observable<number> {
    return this.http.get<{ conversations: number }>(`${this.API_URL}/conversations/unread`).pipe(map(found => found.conversations));
  }

  /** The conversation with its latest messages, or those before one already shown. */
  thread(id: number, before: number | null = null): Observable<MessageThread> {
    return this.http.get<MessageThread>(`${this.API_URL}/conversations/${id}`, before === null ? {} : { params: { before } });
  }

  read(id: number): Observable<Conversation> {
    return this.http.put<Conversation>(`${this.API_URL}/conversations/${id}/read`, null);
  }

  settings(id: number, settings: { muted?: boolean; archived?: boolean }): Observable<Conversation> {
    return this.http.put<Conversation>(`${this.API_URL}/conversations/${id}/settings`, settings);
  }

  send(id: number, text: string, replyTo: number | null = null): Observable<Message> {
    return this.http.post<Message>(`${this.API_URL}/conversations/${id}/messages`, { text, replyTo });
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

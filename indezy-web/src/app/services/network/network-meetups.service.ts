import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MeetupAttendance, MeetupAttendees, NetworkMeetup, NetworkMeetups, NewMeetup } from '../../models';

/** Which events are listed: the coming ones, those the reader answered, or those they organize. */
export type MeetupScope = 'UPCOMING' | 'ATTENDING' | 'ORGANIZED';

/** The member network's events: meetups and webinars, organized, answered, and added to a calendar. */
@Injectable({ providedIn: 'root' })
export class NetworkMeetupsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/network/events`;

  list(scope: MeetupScope = 'UPCOMING', tag: string | null = null, typed = '', page = 0): Observable<NetworkMeetups> {
    let params = new HttpParams().set('scope', scope).set('q', typed).set('page', page);
    if (tag) {
      params = params.set('tag', tag);
    }
    return this.http.get<NetworkMeetups>(this.url, { params });
  }

  get(id: number): Observable<NetworkMeetup> {
    return this.http.get<NetworkMeetup>(`${this.url}/${id}`);
  }

  create(meetup: NewMeetup): Observable<NetworkMeetup> {
    return this.http.post<NetworkMeetup>(this.url, meetup);
  }

  update(id: number, meetup: NewMeetup): Observable<NetworkMeetup> {
    return this.http.put<NetworkMeetup>(`${this.url}/${id}`, meetup);
  }

  /** Cancels an event, those who answered it told. */
  cancel(id: number): Observable<NetworkMeetup> {
    return this.http.delete<NetworkMeetup>(`${this.url}/${id}`);
  }

  /** Answers an event Going or Interested, or takes the answer back (null). */
  attend(id: number, status: MeetupAttendance | null): Observable<NetworkMeetup> {
    return status ? this.http.put<NetworkMeetup>(`${this.url}/${id}/attendance`, null, { params: { status } }) :
      this.http.delete<NetworkMeetup>(`${this.url}/${id}/attendance`);
  }

  attendees(id: number): Observable<MeetupAttendees> {
    return this.http.get<MeetupAttendees>(`${this.url}/${id}/attendees`);
  }

  /** The event as an iCalendar file. */
  ics(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/ics`, { responseType: 'blob' });
  }
}

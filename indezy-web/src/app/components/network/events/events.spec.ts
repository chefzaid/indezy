import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { NetworkMeetup, NewMeetup } from '../../../models';
import { NetworkMeetupsService } from '../../../services/network/network-meetups.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';
import { EventCardComponent } from './event-card.component';
import { EventFormComponent, localInput, parseTags } from './event-form.component';
import { NetworkEventComponent } from './network-event.component';
import { NetworkEventsComponent } from './network-events.component';

const organizer = { handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', avatar: null, headline: null, stack: [], city: null,
  remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
const meetup = (id: number, changes: Partial<NetworkMeetup> = {}): NetworkMeetup => ({ id, title: `Apéro ${id}`, description: 'On parle Kafka',
  startsAt: '2026-12-03T18:30:00', endsAt: null, online: false, place: 'Lyon', link: null, tags: ['kafka'], organizer, going: 3, interested: 1,
  mine: null, own: false, editable: false, cancelled: false, ...changes });

function translations(): void {
  const translate = TestBed.inject(TranslateService);
  translate.setTranslation('en', en);
  translate.use('en');
}

describe('event helpers', () => {
  it('reads tags typed with or without #, once each, and a datetime-local value', () => {
    expect(parseTags('#Kafka, lyon  #kafka #')).toEqual(['kafka', 'lyon']);
    expect(localInput('2026-12-03T18:30:00')).toBe('2026-12-03T18:30');
    expect(localInput(null)).toBe('');
  });
});

describe('EventFormComponent', () => {
  function create(initial: NetworkMeetup | null = null) {
    TestBed.configureTestingModule({ imports: [EventFormComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }] });
    translations();
    const fixture = TestBed.createComponent(EventFormComponent);
    fixture.componentRef.setInput('initial', initial);
    fixture.detectChanges();
    return fixture;
  }

  it('needs an address in a place, a link online, and an end after the start', () => {
    const fixture = create();
    const form = fixture.componentInstance.form;
    const saved: NewMeetup[] = [];
    fixture.componentInstance.saved.subscribe(value => saved.push(value));
    form.patchValue({ title: 'Apéro Kafka', startsAt: '2026-12-03T18:30', endsAt: '2026-12-03T18:00' });
    expect(form.hasError('where')).toBeTrue();
    expect(form.hasError('order')).toBeTrue();
    form.patchValue({ place: ' Le Comptoir ', endsAt: '2026-12-03T21:00', tags: '#Kafka lyon', description: '  ' });
    expect(form.valid).toBeTrue();
    fixture.componentInstance.submit();
    expect(saved).toEqual([{ title: 'Apéro Kafka', description: null, startsAt: '2026-12-03T18:30', endsAt: '2026-12-03T21:00', online: false,
      place: 'Le Comptoir', link: null, tags: ['kafka', 'lyon'] }]);
    form.patchValue({ online: true, link: 'pas un lien' });
    expect(form.invalid).toBeTrue();
    form.patchValue({ link: 'https://meet.example.org/kafka' });
    fixture.componentInstance.submit();
    expect(saved[1]).toEqual(jasmine.objectContaining({ online: true, place: null, link: 'https://meet.example.org/kafka' }));
  });

  it('starts from the event changed', () => {
    const fixture = create(meetup(4, { endsAt: '2026-12-03T21:00:00', tags: ['kafka', 'lyon'] }));
    expect(fixture.componentInstance.form.getRawValue()).toEqual(jasmine.objectContaining({ title: 'Apéro 4', startsAt: '2026-12-03T18:30',
      endsAt: '2026-12-03T21:00', place: 'Lyon', tags: '#kafka #lyon' }));
  });
});

describe('EventCardComponent', () => {
  let meetups: jasmine.SpyObj<NetworkMeetupsService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(shown: NetworkMeetup, compact = false) {
    meetups = jasmine.createSpyObj('NetworkMeetupsService', ['attend']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({ imports: [EventCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkMeetupsService, useValue: meetups }, { provide: NotificationService, useValue: notifications }] });
    translations();
    const fixture = TestBed.createComponent(EventCardComponent);
    fixture.componentRef.setInput('meetup', shown);
    fixture.componentRef.setInput('compact', compact);
    fixture.detectChanges();
    return fixture;
  }

  it('shows when and where, its tags and counts, and answers Going, then takes it back', () => {
    const fixture = create(meetup(4));
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h3 a')?.getAttribute('href')).toBe('/network/events/4');
    expect(element.textContent).toContain('Lyon');
    expect(element.textContent).toContain('#kafka');
    expect(element.querySelector('[data-event-going]')?.textContent).toContain('3 going');
    const changed: NetworkMeetup[] = [];
    fixture.componentInstance.changed.subscribe(value => changed.push(value));
    meetups.attend.and.returnValue(of(meetup(4, { mine: 'GOING', going: 4 })));
    (element.querySelector('[data-event-going-button]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(meetups.attend).toHaveBeenCalledWith(4, 'GOING');
    expect(element.querySelector('[data-event-going-button]')?.getAttribute('aria-pressed')).toBe('true');
    meetups.attend.and.returnValue(of(meetup(4)));
    fixture.componentInstance.answer('GOING');
    expect(meetups.attend).toHaveBeenCalledWith(4, null);
    expect(changed.length).toBe(2);
    meetups.attend.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.answer('INTERESTED');
    expect(notifications.error).toHaveBeenCalledWith('network.events.saveError');
  });

  it('says Online and Cancelled, with no answers for a cancelled or compact one', () => {
    const element: HTMLElement = create(meetup(5, { online: true, cancelled: true }), true).nativeElement;
    expect(element.textContent).toContain('Online');
    expect(element.querySelector('[data-event-cancelled]')).not.toBeNull();
    expect(element.querySelector('[data-event-going-button]')).toBeNull();
    expect(element.textContent).not.toContain('#kafka');
  });
});

describe('NetworkEventsComponent', () => {
  let meetups: jasmine.SpyObj<NetworkMeetupsService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  function create(list = [meetup(1), meetup(2)]) {
    meetups = jasmine.createSpyObj('NetworkMeetupsService', ['list', 'create', 'attend']);
    meetups.list.and.returnValue(of({ meetups: list, page: 0, more: true }));
    notifications = jasmine.createSpyObj('NotificationService', ['error', 'success']);
    query = new BehaviorSubject(convertToParamMap({}));
    TestBed.configureTestingModule({ imports: [NetworkEventsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { queryParamMap: query } },
        { provide: NetworkMeetupsService, useValue: meetups }, { provide: NotificationService, useValue: notifications }] });
    stubNetworkNav(NetworkEventsComponent);
    stubNetworkColumns(NetworkEventsComponent);
    translations();
    const fixture = TestBed.createComponent(NetworkEventsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the coming events, by scope, tag and words, and loads more', fakeAsync(() => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(meetups.list).toHaveBeenCalledWith('UPCOMING', null, '');
    expect(element.querySelectorAll('app-event-card').length).toBe(2);
    (element.querySelector('[data-events-scope="ATTENDING"]') as HTMLButtonElement).click();
    expect(meetups.list).toHaveBeenCalledWith('ATTENDING', null, '');
    query.next(convertToParamMap({ tag: 'kafka' }));
    fixture.detectChanges();
    expect(meetups.list).toHaveBeenCalledWith('ATTENDING', 'kafka', '');
    expect(element.querySelector('[data-events-tag]')?.textContent).toContain('#kafka');
    fixture.componentInstance.filter.setValue('flink');
    tick(250);
    expect(meetups.list).toHaveBeenCalledWith('ATTENDING', 'kafka', 'flink');
    meetups.list.and.returnValue(of({ meetups: [meetup(2), meetup(3)], page: 1, more: false }));
    fixture.componentInstance.loadMore();
    expect(fixture.componentInstance.list!.map(item => item.id)).toEqual([1, 2, 3]);
    expect(fixture.componentInstance.more).toBeFalse();
    fixture.componentInstance.changed(meetup(1, { mine: 'GOING' }));
    expect(fixture.componentInstance.list![0].mine).toBe('GOING');
  }));

  it('organizes an event and opens its page, saying why it cannot', () => {
    const fixture = create([]);
    expect(fixture.nativeElement.querySelector('[data-events-none]').textContent).toContain('No upcoming events');
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const details: NewMeetup = { title: 'Apéro', description: null, startsAt: '2026-12-03T18:30', endsAt: null, online: false, place: 'Lyon',
      link: null, tags: [] };
    meetups.create.and.returnValue(of(meetup(9)));
    fixture.componentInstance.create(details);
    expect(navigate).toHaveBeenCalledWith(['/network/events', 9]);
    expect(notifications.success).toHaveBeenCalledWith('network.events.created');
    meetups.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    fixture.componentInstance.create(details);
    expect(notifications.error).toHaveBeenCalledWith('network.events.tooMany');
    meetups.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    fixture.componentInstance.create(details);
    expect(notifications.error).toHaveBeenCalledWith('network.events.invalid');
  });
});

describe('NetworkEventComponent', () => {
  let meetups: jasmine.SpyObj<NetworkMeetupsService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;

  function create(found = of(meetup(4, { editable: true, own: true, link: 'https://meetup.example.org/apero' }))) {
    meetups = jasmine.createSpyObj('NetworkMeetupsService', ['get', 'attendees', 'ics', 'update', 'cancel', 'attend']);
    meetups.get.and.returnValue(found);
    meetups.attendees.and.returnValue(of({ going: [organizer], interested: [] }));
    notifications = jasmine.createSpyObj('NotificationService', ['error', 'success']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({ imports: [NetworkEventComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '4' })) } },
        { provide: NetworkMeetupsService, useValue: meetups }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }] });
    stubNetworkNav(NetworkEventComponent);
    stubNetworkColumns(NetworkEventComponent);
    translations();
    const fixture = TestBed.createComponent(NetworkEventComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the event, who goes, its link, and lets its organizer change and cancel it', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(meetups.get).toHaveBeenCalledWith(4);
    expect(element.querySelector('[data-event-about]')?.textContent).toContain('On parle Kafka');
    expect(element.querySelector('[data-event-attendees]')?.textContent).toContain('Inès Bernard');
    expect(element.querySelector('[data-event-open-link]')?.getAttribute('href')).toBe('https://meetup.example.org/apero');
    (element.querySelector('[data-event-edit]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('app-event-form')).not.toBeNull();
    meetups.update.and.returnValue(of(meetup(4, { title: 'Apéro revu', editable: true })));
    fixture.componentInstance.save({ title: 'Apéro revu', description: null, startsAt: '2026-12-03T18:30', endsAt: null, online: false,
      place: 'Lyon', link: null, tags: [] });
    expect(fixture.componentInstance.meetup?.title).toBe('Apéro revu');
    expect(fixture.componentInstance.editing).toBeFalse();
    confirm.confirm.and.returnValue(of(true));
    meetups.cancel.and.returnValue(of(meetup(4, { cancelled: true })));
    fixture.componentInstance.cancel();
    expect(meetups.cancel).toHaveBeenCalledWith(4);
    expect(notifications.success).toHaveBeenCalledWith('network.events.cancelledDone');
    meetups.ics.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.addToCalendar();
    expect(notifications.error).toHaveBeenCalledWith('network.events.icsError');
  });

  it('says when the event no longer exists', () => {
    const element: HTMLElement = create(throwError(() => new HttpErrorResponse({ status: 404 }))).nativeElement;
    expect(element.querySelector('[data-event-missing]')).not.toBeNull();
  });
});

describe('NetworkMeetupsService', () => {
  it('lists, organizes, answers, cancels and downloads events', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkMeetupsService);
    const http = TestBed.inject(HttpTestingController);
    const at = (method: string, path: string) => http.expectOne(call => call.method === method && call.url.endsWith(path));
    service.list().subscribe();
    const all = at('GET', '/network/events').request.params;
    expect([all.get('scope'), all.has('tag')]).toEqual(['UPCOMING', false]);
    service.list('ATTENDING', 'kafka', 'apéro', 1).subscribe();
    const narrowed = at('GET', '/network/events').request.params;
    expect([narrowed.get('scope'), narrowed.get('tag'), narrowed.get('q'), narrowed.get('page')]).toEqual(['ATTENDING', 'kafka', 'apéro', '1']);
    const details: NewMeetup = { title: 'Apéro', description: null, startsAt: '2026-12-03T18:30', endsAt: null, online: false, place: 'Lyon',
      link: null, tags: [] };
    service.create(details).subscribe();
    expect(at('POST', '/network/events').request.body).toEqual(details);
    service.get(4).subscribe();
    at('GET', '/network/events/4');
    service.update(4, details).subscribe();
    at('PUT', '/network/events/4');
    service.cancel(4).subscribe();
    at('DELETE', '/network/events/4');
    service.attend(4, 'GOING').subscribe();
    expect(at('PUT', '/network/events/4/attendance').request.params.get('status')).toBe('GOING');
    service.attend(4, null).subscribe();
    at('DELETE', '/network/events/4/attendance');
    service.attendees(4).subscribe();
    at('GET', '/network/events/4/attendees');
    service.ics(4).subscribe();
    expect(at('GET', '/network/events/4/ics').request.responseType).toBe('blob');
    http.verify();
  });
});

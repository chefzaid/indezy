import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NetworkMembership } from '../../../../models';
import { NetworkService } from '../../../../services/network/network.service';
import { NotificationService } from '../../../../services/notification/notification.service';
import { ProfileFollowRequestsComponent } from './profile-follow-requests.component';
import en from '../../../../../assets/i18n/en.json';

describe('ProfileFollowRequestsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string, firstName: string): MemberCard => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const membership: NetworkMembership = { joined: true, handle: 'camille', availability: { mode: 'AUTO', date: null }, card: null,
    fields: { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false },
    privateCard: true, esnSuggestions: false };

  function create(requests = of([card('leo_m', 'Léo'), card('ines_b', 'Inès')])) {
    network = jasmine.createSpyObj('NetworkService', ['followRequests', 'answerFollowRequest', 'setPrivate', 'setEsnSuggestions']);
    network.followRequests.and.returnValue(requests);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProfileFollowRequestsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileFollowRequestsComponent);
    fixture.componentRef.setInput('membership', membership);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the requests, approves one and declines another', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Requests to follow (2)');
    network.answerFollowRequest.and.returnValue(of([card('ines_b', 'Inès')]));
    (element.querySelector('[data-follow-request="leo_m"] [data-request-approve]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.answerFollowRequest).toHaveBeenCalledWith('leo_m', true);
    expect(notifications.success).toHaveBeenCalledWith('profile.network.requestApproved');
    expect(element.querySelector('[data-follow-request="leo_m"]')).toBeNull();
    network.answerFollowRequest.and.returnValue(of([]));
    fixture.componentInstance.answer(card('ines_b', 'Inès'), false);
    expect(network.answerFollowRequest).toHaveBeenCalledWith('ines_b', false);
    expect(notifications.success).toHaveBeenCalledWith('profile.network.requestDeclined');
    network.answerFollowRequest.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.answer(card('ines_b', 'Inès'), true);
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  });

  it('turns the card public, the requests then approved, or private again, telling the profile', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const changed = jasmine.createSpy('changed');
    component.changed.subscribe(changed);
    network.setPrivate.and.returnValue(of({ ...membership, privateCard: false, esnSuggestions: false }));
    component.setPrivate(false);
    expect(network.setPrivate).toHaveBeenCalledWith(false);
    expect(component.requests).toEqual([]);
    expect(changed).toHaveBeenCalledWith({ ...membership, privateCard: false, esnSuggestions: false });
    expect(notifications.success).toHaveBeenCalledWith('profile.network.privateOff');
    network.setPrivate.and.returnValue(of(membership));
    component.setPrivate(true);
    expect(notifications.success).toHaveBeenCalledWith('profile.network.privateOn');
    network.setPrivate.and.returnValue(throwError(() => new Error('down')));
    component.setPrivate(true);
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
    expect(component.saving).toBeFalse();
  });

  it('suggests members who went through the same ESNs, or no longer, telling the profile', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const changed = jasmine.createSpy('changed');
    component.changed.subscribe(changed);
    network.setEsnSuggestions.and.returnValue(of({ ...membership, esnSuggestions: true }));
    ((fixture.nativeElement as HTMLElement).querySelector('[data-esn-toggle] input, [data-esn-toggle] button') as HTMLElement).click();
    expect(network.setEsnSuggestions).toHaveBeenCalledWith(true);
    expect(changed).toHaveBeenCalledWith(jasmine.objectContaining({ esnSuggestions: true }));
    expect(notifications.success).toHaveBeenCalledWith('profile.network.esnOn');
    network.setEsnSuggestions.and.returnValue(of(membership));
    component.setEsnSuggestions(false);
    expect(notifications.success).toHaveBeenCalledWith('profile.network.esnOff');
    network.setEsnSuggestions.and.returnValue(throwError(() => new Error('down')));
    component.setEsnSuggestions(true);
    expect(component.saving).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  });

  it('shows no request when they cannot be read', () => {
    const fixture = create(throwError(() => new Error('down')));
    expect(fixture.componentInstance.requests).toEqual([]);
  });
});

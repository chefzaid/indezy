import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, MemberPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberPageComponent } from './member-page.component';
import en from '../../../../assets/i18n/en.json';

describe('MemberPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string, changes: Partial<MemberCard> = {}): MemberCard => ({ handle, firstName: 'Camille', lastName: 'Durand',
    avatar: null, headline: 'Architecte Java', stack: ['Java'], city: 'Lyon', remote: false, availability: 'ON_MISSION',
    availabilityDate: '2027-03-31', languages: [], verified: false, ...changes });
  const page: MemberPage = { card: card('camille'), followers: 2, following: 1, sharedSkills: ['Java'], followed: false, followsYou: true,
    own: false };

  function create(read = of(page)): ComponentFixture<MemberPageComponent> {
    network = jasmine.createSpyObj('NetworkService', ['page', 'follow', 'unfollow', 'followers', 'following', 'removeFollower']);
    network.page.and.returnValue(read);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [MemberPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ handle: 'camille' })) } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(MemberPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the card, the counts, the shared skills and that the member follows the viewer, then follows and unfollows', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(network.page).toHaveBeenCalledWith('camille');
    expect(element.querySelector('[data-member-card="camille"]')).not.toBeNull();
    expect(element.querySelector('[data-followers]')?.textContent).toContain('2 followers');
    expect(element.querySelector('[data-follows-you]')).not.toBeNull();
    expect(element.querySelector('[data-shared-skills]')?.textContent).toContain('Java');

    network.follow.and.returnValue(of({ ...page, followed: true, followers: 3 }));
    fixture.componentInstance.toggleFollow();
    fixture.detectChanges();
    expect(element.querySelector('[data-follow]')?.textContent).toContain('Unfollow');
    network.unfollow.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleFollow();
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
    expect(fixture.componentInstance.isFollowing).toBeFalse();
  });

  it('lists the followers and the following, and lets the viewer remove a follower of their own page', () => {
    const fixture = create(of({ ...page, own: true, followsYou: false, sharedSkills: [] }));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-follow]')).toBeNull();
    network.followers.and.returnValue(of([card('leo', { firstName: 'Léo' }), card('ines')]));
    component.show('followers');
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-member-list="followers"] li').length).toBe(2);
    network.removeFollower.and.returnValue(of(undefined));
    (element.querySelector('[data-remove-follower="leo"]') as HTMLButtonElement).click();
    expect(component.members.map(member => member.handle)).toEqual(['ines']);
    expect(component.page?.followers).toBe(1);
    network.removeFollower.and.returnValue(throwError(() => new Error('down')));
    component.removeFollower(card('ines'));
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');

    network.following.and.returnValue(of([]));
    component.show('following');
    fixture.detectChanges();
    expect(element.querySelector('[data-member-list="following"]')?.textContent).toContain('No member yet.');
    network.following.and.returnValue(throwError(() => new Error('down')));
    component.show('following');
    expect(notifications.error).toHaveBeenCalledWith('network.page.listError');
  });

  it('refreshes the followers listed once the viewer follows', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.followers.and.returnValue(of([card('leo')]));
    component.show('followers');
    network.follow.and.returnValue(of({ ...page, followed: true }));
    component.toggleFollow();
    expect(network.followers).toHaveBeenCalledTimes(2);
  });

  for (const [status, problem] of [[409, 'notMember'], [404, 'notFound'], [500, 'error']] as const) {
    it(`says why the page cannot be shown (${status})`, () => {
      const fixture = create(throwError(() => new HttpErrorResponse({ status })));
      expect(fixture.componentInstance.problem).toBe(problem);
      expect((fixture.nativeElement as HTMLElement).querySelector(`[data-member-problem="${problem}"]`)).not.toBeNull();
    });
  }
});

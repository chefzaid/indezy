import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Conversation, MemberCard, MemberDirectory, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberDirectoryComponent } from './member-directory.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';

describe('MemberDirectoryComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let messages: jasmine.SpyObj<NetworkMessagesService>;
  const card = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: ['Kafka'],
    city: 'Lyon', remote: true, availability: 'LISTENING', availabilityDate: null, languages: [], verified: false });
  const found: MemberDirectory = { members: [card('leo_m'), card('ines_b')], total: 22, page: 0, size: 20, followed: ['ines_b'], requested: [],
    privateCards: [] };
  const page = { card: card('leo_m'), followers: 1, following: 0, sharedSkills: [], followed: false, followsYou: false, own: false,
    muted: false, blocked: false, privateCard: false, requested: false };

  function setUp(answer = of(found)): void {
    network = jasmine.createSpyObj('NetworkService', ['directory', 'follow', 'unfollow', 'membership', 'following', 'followers',
      'followRequests', 'sentFollowRequests', 'answerFollowRequest']);
    network.directory.and.returnValue(answer);
    network.membership.and.returnValue(of({ handle: 'camille' } as NetworkMembership));
    network.following.and.returnValue(of([card('leo_m')]));
    network.followers.and.returnValue(of([card('ines_b'), card('leo_m')]));
    network.followRequests.and.returnValue(of([card('zoe_p')]));
    network.sentFollowRequests.and.returnValue(of([card('paul_d')]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    messages = jasmine.createSpyObj('NetworkMessagesService', ['open']);
    TestBed.configureTestingModule({
      imports: [MemberDirectoryComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: NetworkMessagesService, useValue: messages }]
    });
    stubNetworkNav(MemberDirectoryComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
  }

  function create(answer = of(found)): ComponentFixture<MemberDirectoryComponent> {
    setUp(answer);
    const fixture = TestBed.createComponent(MemberDirectoryComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the members found, each opening their page, and searches again with the criteria and the page', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.directory).toHaveBeenCalledWith({ skill: '', city: '', availability: null, remote: null, page: 0 });
    expect(element.querySelector('[data-directory-count]')?.textContent).toContain('Members found: 22');
    expect(element.querySelector('[data-directory-member="leo_m"]')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('mat-paginator')).not.toBeNull();

    component.form.patchValue({ skill: 'Kafka', remote: true });
    component.onPage({ pageIndex: 1, pageSize: 20, length: 22 });
    expect(network.directory).toHaveBeenCalledWith({ skill: 'Kafka', city: '', availability: null, remote: true, page: 1 });
    network.directory.and.returnValue(of({ members: [], total: 0, page: 0, size: 20, followed: [], requested: [], privateCards: [] }));
    component.search();
    fixture.detectChanges();
    expect(element.textContent).toContain('No member matches.');
  });

  it('filters with the chips, each searching again', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    (element.querySelector('[data-directory-availability-option="LISTENING"]') as HTMLButtonElement).click();
    expect(network.directory).toHaveBeenCalledWith({ skill: '', city: '', availability: 'LISTENING', remote: null, page: 0 });
    (element.querySelector('[data-directory-remote-option="false"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.directory).toHaveBeenCalledWith({ skill: '', city: '', availability: 'LISTENING', remote: false, page: 0 });
    expect(element.querySelector('[data-directory-remote-option="false"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(element.querySelector('[data-directory-remote-option="ANY"]')?.getAttribute('aria-pressed')).toBe('false');
    (element.querySelector('[data-directory-remote-option="ANY"]') as HTMLButtonElement).click();
    (element.querySelector('[data-directory-availability-option="ANY"]') as HTMLButtonElement).click();
    expect(network.directory).toHaveBeenCalledTimes(5);
    expect(network.directory.calls.mostRecent().args[0]).toEqual({ skill: '', city: '', availability: null, remote: null, page: 0 });
  });

  it('shows each member as a tile with the first skills, and follows, asks to follow or unfollows from it', () => {
    const many = { ...card('leo_m'), stack: ['Java', 'Kafka', 'Spark', 'Scala', 'Flink'], verified: true, headline: 'Data engineer' };
    const fixture = create(of({ ...found, members: [many, card('ines_b'), card('zoe_p')], privateCards: ['zoe_p'] }));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const tile = element.querySelector('[data-directory-tile="leo_m"]')!;
    expect(Array.from(tile.querySelectorAll('.skills li')).map(item => item.textContent?.trim())).toEqual(['Java', 'Kafka', 'Spark', '+2']);
    expect(tile.textContent).toContain('Open to offers');
    expect(tile.querySelector('.verified')).not.toBeNull();
    const follow = (handle: string) => element.querySelector(`[data-directory-follow="${handle}"]`) as HTMLButtonElement;
    expect(follow('leo_m').textContent).toContain('Follow');
    expect(follow('ines_b').textContent).toContain('Following');
    expect(follow('zoe_p').textContent).toContain('Ask to follow');

    network.follow.and.returnValue(of({ ...page, followed: true }));
    follow('leo_m').click();
    fixture.detectChanges();
    expect(network.follow).toHaveBeenCalledWith('leo_m');
    expect(follow('leo_m').textContent).toContain('Following');
    network.follow.and.returnValue(of({ ...page, requested: true }));
    follow('zoe_p').click();
    fixture.detectChanges();
    expect(follow('zoe_p').textContent).toContain('Request sent');
    network.unfollow.and.returnValue(of(page));
    follow('ines_b').click();
    fixture.detectChanges();
    expect(network.unfollow).toHaveBeenCalledWith('ines_b');
    expect(follow('ines_b').textContent).toContain('Follow');
    expect(component.directory?.followed).toEqual(['leo_m']);
    follow('zoe_p').click();
    expect(network.unfollow).toHaveBeenCalledWith('zoe_p');
    expect(component.directory?.requested).toEqual([]);

    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.toggleFollow('ines_b');
    expect(notifications.error).toHaveBeenCalledWith('network.page.tooManyFollows');
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.toggleFollow('ines_b');
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
    expect(component.pending.size).toBe(0);
  });

  it('shows the members followed, the followers and the requests in tabs kept in the address, messaging those followed or following', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const tab = (name: string) => element.querySelector(`[data-members-tab="${name}"]`) as HTMLButtonElement;
    expect(Array.from(element.querySelectorAll('[data-members-tab]')).map(item => item.textContent?.trim()))
      .toEqual(['All members', 'Following', 'Followers', 'Requests received 1', 'Requests sent']);
    expect(tab('all').getAttribute('aria-selected')).toBe('true');
    // The reader follows Ines: she can be written to from the directory; Léo cannot, yet.
    expect(element.querySelector('[data-directory-message="ines_b"]')).not.toBeNull();
    expect(element.querySelector('[data-directory-message="leo_m"]')).toBeNull();

    tab('following').click();
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { tab: 'following' } }));
    expect(network.following).toHaveBeenCalledWith('camille');
    expect(element.querySelectorAll('[data-members-list="following"] [data-directory-tile]').length).toBe(1);
    expect((element.querySelector('[data-directory-follow="leo_m"]') as HTMLElement).textContent).toContain('Following');
    messages.open.and.returnValue(of({ id: 4 } as Conversation));
    (element.querySelector('[data-directory-message="leo_m"]') as HTMLButtonElement).click();
    expect(messages.open).toHaveBeenCalledWith('leo_m');
    expect(navigate).toHaveBeenCalledWith(['/network/messages', 4]);
    messages.open.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.message('leo_m');
    expect(notifications.error).toHaveBeenCalledWith('network.messages.openError');

    tab('followers').click();
    fixture.detectChanges();
    expect(element.querySelector('[data-directory-message="ines_b"]')).not.toBeNull();
    expect((element.querySelector('[data-directory-follow="ines_b"]') as HTMLElement).textContent).toContain('Follow');

    tab('received').click();
    fixture.detectChanges();
    expect(element.querySelector('[data-directory-follow]')).toBeNull();
    network.answerFollowRequest.and.returnValue(of([]));
    (element.querySelector('[data-approve="zoe_p"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.answerFollowRequest).toHaveBeenCalledWith('zoe_p', true);
    expect(notifications.success).toHaveBeenCalledWith('network.members.approved');
    expect(component.received).toBe(0);
    expect(element.querySelector('[data-members-empty="received"]')?.textContent).toContain('No request waiting');
    expect(component.canMessage('zoe_p')).toBeTrue();
    network.answerFollowRequest.and.returnValue(throwError(() => new Error('down')));
    component.answer('paul_d', false);
    expect(notifications.error).toHaveBeenCalledWith('network.members.answerError');

    tab('sent').click();
    fixture.detectChanges();
    expect((element.querySelector('[data-directory-follow="paul_d"]') as HTMLElement).textContent).toContain('Request sent');
    network.unfollow.and.returnValue(of({ ...page, card: card('paul_d'), privateCard: true }));
    (element.querySelector('[data-directory-follow="paul_d"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.unfollow).toHaveBeenCalledWith('paul_d');
    expect((element.querySelector('[data-directory-follow="paul_d"]') as HTMLElement).textContent).toContain('Ask to follow');

    tab('all').click();
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { tab: null } }));
    expect(network.directory).toHaveBeenCalledTimes(2);
  });

  it('opens the tab the address names, and says when a list cannot be read', async () => {
    setUp();
    await TestBed.inject(Router).navigateByUrl('/?tab=received');
    let fixture = TestBed.createComponent(MemberDirectoryComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.tab).toBe('received');
    expect(network.directory).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-approve="zoe_p"]')).not.toBeNull();
    TestBed.resetTestingModule();
    setUp();
    network.sentFollowRequests.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    network.followRequests.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await TestBed.inject(Router).navigateByUrl('/?tab=sent');
    fixture = TestBed.createComponent(MemberDirectoryComponent);
    fixture.detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('network.directory.loadError');
    expect(fixture.componentInstance.received).toBeNull();
  });

  it('asks a visitor to join first, and says when the members cannot be loaded', () => {
    let fixture = create(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-directory-not-member]')).not.toBeNull();
    expect(notifications.error).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(fixture.componentInstance.notMember).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('network.directory.loadError');
  });
});

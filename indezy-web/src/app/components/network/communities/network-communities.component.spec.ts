import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { Community } from '../../../models';
import { NetworkCommunitiesService } from '../../../services/network/network-communities.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkCommunitiesComponent } from './network-communities.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('NetworkCommunitiesComponent', () => {
  let communities: jasmine.SpyObj<NetworkCommunitiesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const community = (slug: string, changes: Partial<Community> = {}): Community => ({ slug, name: slug.toUpperCase(), description: 'Les fans',
    members: 3, joined: false, moderator: false, privateCommunity: false, requested: false, ...changes });

  function create(list: Observable<Community[]> = of([community('kafka', { joined: true, moderator: true }), community('lyon', { members: 1 })])) {
    communities = jasmine.createSpyObj('NetworkCommunitiesService', ['list', 'create', 'join']);
    communities.list.and.returnValue(list);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [NetworkCommunitiesComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkCommunitiesService, useValue: communities }, { provide: NotificationService, useValue: notifications }]
    });
    stubNetworkNav(NetworkCommunitiesComponent);
    stubNetworkColumns(NetworkCommunitiesComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkCommunitiesComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the communities, finds them by name and joins one', fakeAsync(() => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-community="kafka"]')?.textContent).toContain('Moderator');
    expect(element.querySelector('[data-community="kafka"] a')?.getAttribute('href')).toBe('/network/communities/kafka');
    expect(element.querySelector('[data-community="lyon"]')?.textContent).toContain('1 member');
    fixture.componentInstance.filter.setValue(' kaf ');
    tick(250);
    expect(communities.list).toHaveBeenCalledWith('kaf');
    communities.join.and.returnValue(of({ community: community('lyon', { joined: true, members: 2 }), moderators: [], creator: false }));
    (element.querySelector('[data-community-join="lyon"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-community="lyon"]')?.textContent).toContain('Member');
    communities.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    fixture.componentInstance.join(community('lyon'));
    expect(notifications.error).toHaveBeenCalledWith('network.communities.removedFrom');
  }));

  it('creates a community and opens it, saying when a member made too many', () => {
    const fixture = create(of([]));
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.querySelector('[data-communities-none]')).not.toBeNull();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    (fixture.nativeElement.querySelector('[data-community-new]') as HTMLButtonElement).click();
    fixture.detectChanges();
    component.name.setValue(' Kafka Lyon ');
    communities.create.and.returnValue(of(community('kafka-lyon')));
    component.create();
    expect(communities.create).toHaveBeenCalledWith('Kafka Lyon', null, false);
    expect(navigate).toHaveBeenCalledWith(['/network/communities', 'kafka-lyon']);
    communities.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.create();
    expect(notifications.error).toHaveBeenCalledWith('network.communities.tooMany');
    component.name.setValue('');
    component.create();
    expect(communities.create).toHaveBeenCalledTimes(2);
  });

  it('marks a private community, asks to join it, and says a request waits', () => {
    const fixture = create(of([community('secret', { privateCommunity: true }), community('asked', { privateCommunity: true, requested: true })]));
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-community="secret"] [data-community-is-private]')).not.toBeNull();
    expect(element.querySelector('[data-community-join="secret"]')?.textContent).toContain('Ask to join');
    expect(element.querySelector('[data-community="asked"] [data-community-requested]')?.textContent).toContain('Request sent');
    const component = fixture.componentInstance;
    component.creating = true;
    component.privateCommunity.setValue(true);
    component.name.setValue('Secret');
    communities.create.and.returnValue(of(community('secret-2')));
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    component.create();
    expect(communities.create).toHaveBeenCalledWith('Secret', null, true);
  });

  it('says when the communities cannot be loaded', () => {
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('network.communities.loadError');
  });
});

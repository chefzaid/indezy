import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberPage, MemberSuggestion } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkSideComponent } from './network-side.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkSideComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const suggestion = (handle: string, reason: MemberSuggestion['reason'] = 'SAME_CITY'): MemberSuggestion => ({ reason, followedBy: 0,
    followedByName: null, sharedSkills: [], esn: null, card: { handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null,
      stack: [], city: 'Lyon', remote: false, availability: null, availabilityDate: null, languages: [], verified: false } });

  function create(exclude: string | null = null, suggestions = of(['leo_m', 'ines_b', 'paul_d', 'zoe_l', 'jade_k'].map(handle =>
    suggestion(handle)))) {
    network = jasmine.createSpyObj('NetworkService', ['suggestions', 'trendingTags', 'follow']);
    network.suggestions.and.returnValue(suggestions);
    network.trendingTags.and.returnValue(of(['java', 'kafka', 'spark', 'go', 'rust', 'sql'].map((tag, index) => ({ tag, posts: 6 - index }))));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [NetworkSideComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkSideComponent);
    fixture.componentRef.setInput('exclude', exclude);
    fixture.detectChanges();
    return fixture;
  }

  it('suggests four members, not the one whose page it is, each with its reason, and the five tags trending', () => {
    const fixture = create('leo_m');
    const element: HTMLElement = fixture.nativeElement;
    expect(Array.from(element.querySelectorAll('[data-side-suggestion]')).map(item => item.getAttribute('data-side-suggestion')))
      .toEqual(['ines_b', 'paul_d', 'zoe_l', 'jade_k']);
    expect(element.querySelector('[data-side-suggestion="ines_b"] .why')?.textContent).toContain('Also in Lyon');
    expect(element.querySelector('[data-side-all-members]')?.getAttribute('href')).toBe('/network/members');
    expect(element.querySelectorAll('[data-side-tag]').length).toBe(5);
    expect(element.querySelector('[data-side-tag="java"]')?.getAttribute('href')).toBe('/network/tags/java');
  });

  it('follows a member, who leaves the card, and says when the follow fails', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const followed: string[] = [];
    component.followed.subscribe(handle => followed.push(handle));
    network.follow.and.returnValue(of({} as MemberPage));
    (fixture.nativeElement.querySelector('[data-side-suggestion="leo_m"] [data-side-follow]') as HTMLButtonElement).click();
    expect(network.follow).toHaveBeenCalledWith('leo_m');
    expect(followed).toEqual(['leo_m']);
    expect(component.suggestions.map(item => item.card.handle)).not.toContain('leo_m');
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.follow(component.suggestions[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.page.tooManyFollows');
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.follow(component.suggestions[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
  });

  it('leaves out a card with nothing to show', () => {
    const fixture = create(null, throwError(() => new Error('down')));
    expect(fixture.nativeElement.querySelector('[data-side-suggestions]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-side-trending]')).not.toBeNull();
  });
});

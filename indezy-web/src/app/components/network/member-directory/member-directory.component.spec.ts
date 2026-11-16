import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, MemberDirectory } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberDirectoryComponent } from './member-directory.component';
import en from '../../../../assets/i18n/en.json';

describe('MemberDirectoryComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: ['Kafka'],
    city: 'Lyon', remote: true, availability: 'LISTENING', availabilityDate: null, languages: [], verified: false });
  const found: MemberDirectory = { members: [card('leo_m'), card('ines_b')], total: 22, page: 0, size: 20, followed: ['ines_b'], requested: [],
    privateCards: [] };
  const page = { card: card('leo_m'), followers: 1, following: 0, sharedSkills: [], followed: false, followsYou: false, own: false,
    muted: false, blocked: false, privateCard: false, requested: false };

  function create(answer = of(found)): ComponentFixture<MemberDirectoryComponent> {
    network = jasmine.createSpyObj('NetworkService', ['directory', 'follow', 'unfollow']);
    network.directory.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [MemberDirectoryComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
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

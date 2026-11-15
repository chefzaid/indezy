import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NetworkPost, ProjectDto } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MissionTakeComponent } from './mission-take.component';
import en from '../../../../assets/i18n/en.json';

describe('MissionTakeComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const author: MemberCard = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  const mission: NetworkPost = { id: 5, author, text: 'Mission', tags: [], createdAt: '2026-11-15T09:00:00', own: false, likes: 0,
    liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
    kind: 'MISSION', details: { role: 'Lead Java', rateMin: null, rateMax: null, city: null, remoteDays: null, start: null, client: null,
      stack: [] }, answerCommentId: null, poll: null, taken: { count: null, mine: false } };

  function create(post = mission) {
    network = jasmine.createSpyObj('NetworkService', ['take', 'takers']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [MissionTakeComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(MissionTakeComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    return fixture;
  }

  it('adds the mission to the pipeline, named or not, then links to the opportunity', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-mission-add]')).not.toBeNull();
    network.take.and.returnValue(of({ id: 42 } as ProjectDto));
    component.take(true);
    fixture.detectChanges();
    expect(network.take).toHaveBeenCalledWith(5, true);
    expect(notifications.success).toHaveBeenCalledWith('network.pipeline.added');
    expect(element.querySelector('[data-mission-taken]')).not.toBeNull();
    expect(element.querySelector('[data-mission-open]')?.getAttribute('href')).toBe('/projects/42');

    network.take.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.take(false);
    expect(notifications.error).toHaveBeenCalledWith('network.pipeline.already');
    network.take.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.take(false);
    expect(notifications.error).toHaveBeenCalledWith('network.pipeline.error');
    expect(component.taking).toBeFalse();
  });

  it('shows its author how many took it, and those who let them know', () => {
    const fixture = create({ ...mission, own: true, taken: { count: 3, mine: false } });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-mission-takers]')?.textContent).toContain('Taken by 3 members');
    network.takers.and.returnValue(of([author]));
    (element.querySelector('[data-mission-takers]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-mission-takers-list]')?.textContent).toContain('Léo Martin');
    fixture.componentInstance.toggleTakers();
    expect(fixture.componentInstance.takers).toBeNull();
    network.takers.and.returnValue(of([]));
    fixture.componentInstance.toggleTakers();
    fixture.detectChanges();
    expect(element.querySelector('[data-mission-takers-list]')?.textContent).toContain('None let you know');
    fixture.componentInstance.takers = null;
    network.takers.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleTakers();
    expect(notifications.error).toHaveBeenCalledWith('network.pipeline.error');
  });
});

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
  const found: MemberDirectory = { members: [card('leo_m'), card('ines_b')], total: 22, page: 0, size: 20 };

  function create(answer = of(found)): ComponentFixture<MemberDirectoryComponent> {
    network = jasmine.createSpyObj('NetworkService', ['directory']);
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
    network.directory.and.returnValue(of({ members: [], total: 0, page: 0, size: 20 }));
    component.search();
    fixture.detectChanges();
    expect(element.textContent).toContain('No member matches.');
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

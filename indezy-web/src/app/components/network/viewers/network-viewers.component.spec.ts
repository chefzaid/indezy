import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { MemberCard, PageViews } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkViewersComponent } from './network-viewers.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkViewersComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Lead Kafka',
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const seen: PageViews = { viewers: 3, viewsThisWeek: 4, named: [{ card: card('leo_m'), viewedAt: '2026-11-19T10:00:00' },
    { card: card('ines_b'), viewedAt: '2026-11-18T10:00:00' }], anonymous: 1, privateViewing: false };

  function create(read: Observable<PageViews> = of(seen)) {
    network = jasmine.createSpyObj('NetworkService', ['pageViews', 'setPrivateViewing']);
    network.pageViews.and.returnValue(read);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [NetworkViewersComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkViewersComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows how many viewed the page, who, and how many browsed privately', () => {
    const element: HTMLElement = create().nativeElement;
    expect(element.querySelector('[data-viewers-total]')?.textContent).toContain('3');
    expect(element.querySelector('[data-viewers-week]')?.textContent).toContain('4 views this week');
    expect(element.querySelectorAll('[data-viewer]').length).toBe(2);
    expect(element.querySelector('[data-viewer="leo_m"] a')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('[data-viewers-anonymous]')?.textContent).toContain('1 member browsing privately');
  });

  it('browses privately, the names then hidden, and puts the switch back when it fails', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.setPrivateViewing.and.returnValue(of({ ...seen, named: [], anonymous: 3, privateViewing: true }));
    const source = { checked: true };
    component.setPrivate({ checked: true, source } as unknown as MatSlideToggleChange);
    fixture.detectChanges();
    expect(network.setPrivateViewing).toHaveBeenCalledWith(true);
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-viewer]').length).toBe(0);
    expect(element.querySelector('[data-viewers-anonymous]')?.textContent).toContain('unnamed while you browse privately');
    network.setPrivateViewing.and.returnValue(throwError(() => new Error('down')));
    component.setPrivate({ checked: false, source } as unknown as MatSlideToggleChange);
    expect(source.checked).toBeTrue();
    expect(component.saving).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('network.viewers.saveError');
  });

  it('says when no one viewed the page, and when the viewers cannot be read', () => {
    let element: HTMLElement = create(of({ viewers: 0, viewsThisWeek: 0, named: [], anonymous: 0, privateViewing: false })).nativeElement;
    expect(element.querySelector('[data-viewers-none]')).not.toBeNull();
    TestBed.resetTestingModule();
    element = create(throwError(() => new Error('down'))).nativeElement;
    expect(notifications.error).toHaveBeenCalledWith('network.viewers.loadError');
    expect(element.querySelector('[data-viewers-total]')).toBeNull();
  });
});

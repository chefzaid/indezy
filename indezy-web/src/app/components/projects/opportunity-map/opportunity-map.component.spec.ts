import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { OpportunityMap, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OpportunityMapComponent } from './opportunity-map.component';

describe('OpportunityMapComponent', () => {
  let fixture: ComponentFixture<OpportunityMapComponent>;
  let commute: jasmine.SpyObj<CommuteService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const data: OpportunityMap = {
    home: { latitude: 48.869, longitude: 2.331 },
    pins: [
      { projectId: 1, role: '<img src=x onerror=alert(1)>', clientName: 'Acme', status: ProjectStatus.CONTACT, place: 'Lyon',
        latitude: 45.764, longitude: 4.836 },
      { projectId: 2, role: 'Lead Java', clientName: null, status: ProjectStatus.WON, place: '92400 Courbevoie', latitude: 48.898,
        longitude: 2.257 }
    ],
    unplaced: 1
  };

  function create(answer: OpportunityMap | Error, user: { id: number } | null = { id: 1 }): HTMLElement {
    commute = jasmine.createSpyObj('CommuteService', ['getMap']);
    commute.getMap.and.returnValue(answer instanceof Error ? throwError(() => answer) : of(answer));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [OpportunityMapComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: CommuteService, useValue: commute }, { provide: NotificationService, useValue: notifications },
        { provide: AuthService, useValue: { getUser: () => user } }]
    });
    fixture = TestBed.createComponent(OpportunityMapComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => fixture?.destroy());

  it('pins each opportunity and the home, says how many could not be placed, and loads the map styles once', () => {
    const element = create(data);

    expect(commute.getMap).toHaveBeenCalledWith(1);
    expect(element.querySelectorAll('path.leaflet-interactive').length).toBe(3);
    expect(element.querySelectorAll('path.pin-WON').length).toBe(1);
    expect(element.querySelector('[data-map-summary]')?.textContent).toContain('projects.map.unplaced');
    expect(document.querySelectorAll('link[data-leaflet-styles]').length).toBe(1);
    fixture.destroy();
    TestBed.resetTestingModule();
    create(data);
    expect(document.querySelectorAll('link[data-leaflet-styles]').length).toBe(1);
  });

  it('shows a role as text in its popup and opens the opportunity from it', () => {
    const element = create(data);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    (element.querySelector('path.pin-CONTACT') as SVGElement).dispatchEvent(new MouseEvent('click', { bubbles: true }));
    const link = element.querySelector('.pin-popup a') as HTMLAnchorElement;
    expect(link.textContent).toBe('<img src=x onerror=alert(1)>');
    expect(element.querySelector('.pin-popup img')).toBeNull();
    expect(element.querySelector('.pin-popup span')?.textContent).toContain('Acme · projects.statuses.CONTACT · Lyon');
    link.click();
    expect(navigate).toHaveBeenCalledWith(['/projects', 1]);
  });

  it('centres on a lone point, points to the profile without a home, and says when the map cannot load', () => {
    const element = create({ home: null, pins: [data.pins[1]], unplaced: 0 });
    expect(element.querySelectorAll('path.leaflet-interactive').length).toBe(1);
    expect(element.querySelector('[data-map-summary] a')?.getAttribute('href')).toBe('/profile');
    fixture.destroy();
    TestBed.resetTestingModule();
    create(new Error('down'));
    expect(notifications.error).toHaveBeenCalledWith('projects.map.loadError');
    fixture.destroy();
    TestBed.resetTestingModule();
    create(data, null);
    expect(commute.getMap).not.toHaveBeenCalled();
  });
});

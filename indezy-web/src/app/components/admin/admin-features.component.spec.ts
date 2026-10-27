import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { FeatureState } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from '../../services/admin/capabilities.service';
import { NotificationService } from '../../services/notification/notification.service';
import { AdminFeaturesComponent } from './admin-features.component';
import en from '../../../assets/i18n/en.json';

describe('AdminFeaturesComponent', () => {
  let admin: jasmine.SpyObj<AdminService>;
  let capabilities: jasmine.SpyObj<CapabilitiesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const states: FeatureState[] = [
    { flag: 'OFFER_FEED', enabled: true }, { flag: 'PUBLIC_PROFILES', enabled: true },
    { flag: 'DATA_IMPORT', enabled: true }, { flag: 'YEARLY_REVIEW', enabled: true }
  ];

  function create(): ComponentFixture<AdminFeaturesComponent> {
    TestBed.configureTestingModule({
      imports: [AdminFeaturesComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AdminService, useValue: admin },
        { provide: CapabilitiesService, useValue: capabilities },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AdminFeaturesComponent);
    fixture.detectChanges();
    return fixture;
  }

  const change = (checked: boolean): MatSlideToggleChange => ({ checked, source: { checked } as MatSlideToggle });

  beforeEach(() => {
    admin = jasmine.createSpyObj('AdminService', ['features', 'setFeature']);
    capabilities = jasmine.createSpyObj('CapabilitiesService', ['refresh']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    admin.features.and.returnValue(of(states));
    capabilities.refresh.and.returnValue(of(DEFAULT_CAPABILITIES));
  });

  it('names and describes every feature with its switch', () => {
    const element: HTMLElement = create().nativeElement;

    expect(element.querySelectorAll('[data-feature]').length).toBe(4);
    expect(element.querySelector('[data-feature="OFFER_FEED"]')?.textContent).toContain('Offer feed');
    expect(element.querySelector('[data-feature="OFFER_FEED"] button[role="switch"]')?.getAttribute('aria-label')).toBe('Offer feed');
  });

  it('turns a feature off, then reads this account\'s capabilities again', () => {
    const component = create().componentInstance;
    const off = states.map(state => state.flag === 'OFFER_FEED' ? { ...state, enabled: false } : state);
    admin.setFeature.and.returnValue(of(off));

    component.changed(states[0], change(false));

    expect(admin.setFeature).toHaveBeenCalledWith('OFFER_FEED', false);
    expect(capabilities.refresh).toHaveBeenCalled();
    expect(component.features[0].enabled).toBeFalse();
    expect(notifications.success).toHaveBeenCalledWith('admin.featureOff');
  });

  it('turns a feature on', () => {
    const component = create().componentInstance;
    admin.setFeature.and.returnValue(of(states));

    component.changed({ flag: 'DATA_IMPORT', enabled: false }, change(true));

    expect(notifications.success).toHaveBeenCalledWith('admin.featureOn');
  });

  it('puts the switch back when the change cannot be saved', () => {
    const component = create().componentInstance;
    admin.setFeature.and.returnValue(throwError(() => new Error('500')));
    const failed = change(false);

    component.changed(states[0], failed);

    expect(failed.source.checked).toBeTrue();
    expect(notifications.error).toHaveBeenCalledWith('admin.saveError');
  });

  it('says when the features cannot be read', () => {
    admin.features.and.returnValue(throwError(() => new Error('403')));
    create();

    expect(notifications.error).toHaveBeenCalledWith('admin.loadError');
  });
});

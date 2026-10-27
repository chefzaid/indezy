import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AdminOverview } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { AdminComponent } from './admin.component';
import en from '../../../assets/i18n/en.json';

describe('AdminComponent', () => {
  let admin: jasmine.SpyObj<AdminService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const overview: AdminOverview = {
    accounts: 40, disabledAccounts: 2, signupsLast30Days: 5,
    featureWorkspaces: { seasons: 10, opportunities: 30, attachments: 10 }
  };

  function create(): ComponentFixture<AdminComponent> {
    TestBed.configureTestingModule({
      imports: [AdminComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: AdminService, useValue: admin },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    admin = jasmine.createSpyObj('AdminService', ['overview', 'accounts', 'setDisabled', 'features', 'setFeature']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
  });

  it('shows the accounts and every feature by its use, most used first', () => {
    admin.overview.and.returnValue(of(overview));
    const fixture = create();
    const page: HTMLElement = fixture.nativeElement;

    expect(page.querySelector('[data-admin-figures]')?.textContent).toContain('40');
    const rows = Array.from(page.querySelectorAll('[data-admin-adoption] tbody tr')).map(row =>
      Array.from(row.querySelectorAll('td')).map(cell => cell.textContent?.trim()).join(' '));
    expect(rows).toEqual(['Opportunities 30 75 %', 'Attachments 10 25 %', 'Seasons 10 25 %']);
  });

  it('counts no share without accounts', () => {
    admin.overview.and.returnValue(of({ ...overview, accounts: 0 }));
    expect(create().componentInstance.share(3)).toBe(0);
  });

  it('says when the console cannot be loaded', () => {
    admin.overview.and.returnValue(throwError(() => new Error('403')));
    const fixture = create();

    expect(notifications.error).toHaveBeenCalledWith('admin.loadError');
    expect(fixture.componentInstance.overview).toBeUndefined();
  });
});

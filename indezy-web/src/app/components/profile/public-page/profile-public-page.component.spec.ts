import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NEVER, of, throwError } from 'rxjs';

import { ProfilePublicPageComponent } from './profile-public-page.component';
import { PublicProfileService } from '../../../services/public-profile/public-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { slugFrom } from '../../../models';

describe('ProfilePublicPageComponent', () => {
  let fixture: ComponentFixture<ProfilePublicPageComponent>;
  let component: ProfilePublicPageComponent;
  let profiles: jasmine.SpyObj<PublicProfileService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let clipboard: jasmine.SpyObj<Clipboard>;

  const create = (): void => {
    fixture = TestBed.createComponent(ProfilePublicPageComponent);
    component = fixture.componentInstance;
    component.name = 'Hélène Dupont-Martin';
    fixture.detectChanges();
  };

  beforeEach(async () => {
    profiles = jasmine.createSpyObj<PublicProfileService>('PublicProfileService', ['settings', 'save']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);
    clipboard = jasmine.createSpyObj<Clipboard>('Clipboard', ['copy']);
    profiles.settings.and.returnValue(of({ enabled: false, slug: null }));
    profiles.save.and.callFake(settings => of(settings));
    clipboard.copy.and.returnValue(true);
    await TestBed.configureTestingModule({
      imports: [ProfilePublicPageComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        { provide: PublicProfileService, useValue: profiles },
        { provide: NotificationService, useValue: notifications },
        { provide: Clipboard, useValue: clipboard }
      ]
    }).compileComponents();
  });

  it('offers an address from the name the first time and shows the profile with it', () => {
    create();
    expect(component.form.getRawValue()).toEqual({ enabled: false, slug: 'helene-dupont-martin' });
    expect(component.link).toBeNull();

    component.form.controls.enabled.setValue(true);
    component.save();

    expect(profiles.save).toHaveBeenCalledWith({ enabled: true, slug: 'helene-dupont-martin' });
    expect(component.link).toBe(`${location.origin}/p/helene-dupont-martin`);
    expect(notifications.success).toHaveBeenCalledWith('profile.publicPage.shown');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-public-link] a')?.getAttribute('href'))
      .toBe(`${location.origin}/p/helene-dupont-martin`);

    component.copyLink();
    expect(clipboard.copy).toHaveBeenCalledWith(`${location.origin}/p/helene-dupont-martin`);
    expect(notifications.success).toHaveBeenCalledWith('profile.publicPage.copied');
  });

  it('keeps a saved address, notes a hidden one and hides it on request', () => {
    profiles.settings.and.returnValue(of({ enabled: true, slug: 'alice' }));
    create();
    expect(component.form.getRawValue()).toEqual({ enabled: true, slug: 'alice' });

    component.form.controls.enabled.setValue(false);
    component.save();
    fixture.detectChanges();
    expect(notifications.success).toHaveBeenCalledWith('profile.publicPage.hidden');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('profile.publicPage.hiddenNote');
  });

  it('refuses showing without an address, a malformed or taken address, and reports failures', () => {
    create();
    component.form.setValue({ enabled: true, slug: '  ' });
    component.save();
    expect(component.form.controls.slug.hasError('required')).toBeTrue();

    component.form.setValue({ enabled: true, slug: 'Bad_Name' });
    component.save();
    expect(profiles.save).not.toHaveBeenCalled();

    profiles.save.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.form.setValue({ enabled: true, slug: 'taken' });
    component.save();
    expect(component.form.controls.slug.hasError('taken')).toBeTrue();

    profiles.save.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.form.setValue({ enabled: false, slug: 'alice' });
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('profile.publicPage.saveError');

    profiles.save.and.returnValue(NEVER);
    component.save();
    component.save();
    expect(profiles.save).toHaveBeenCalledTimes(3);
  });

  it('reports settings that cannot be loaded, and copies nothing without a link', () => {
    profiles.settings.and.returnValue(throwError(() => new Error('down')));
    create();
    expect(notifications.error).toHaveBeenCalledWith('profile.publicPage.loadError');
    component.copyLink();
    expect(clipboard.copy).not.toHaveBeenCalled();
  });

  it('builds addresses from names', () => {
    expect(slugFrom('  Élodie  O\'Brien -- Java ')).toBe('elodie-o-brien-java');
    expect(slugFrom('a'.repeat(39) + ' b')).toBe('a'.repeat(39));
    expect(slugFrom('')).toBe('');
  });
});

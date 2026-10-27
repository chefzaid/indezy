import { fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { profileTestBed, preferences } from './profile-test-bed.spec';

describe('ProfileComponent settings', () => {
  const bed = profileTestBed();

  describe('preferences', () => {
    it('saves the preferences and applies the theme', () => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = bed.create().componentInstance;
      const reload = spyOn(component, 'reloadPage');
      bed.users.updateUserPreferences.and.returnValue(of(preferences));

      component.preferencesForm.patchValue({ autoArchive: true, contactRetentionYears: 3 });
      component.onUpdatePreferences();

      expect(bed.users.updateUserPreferences).toHaveBeenCalledWith(jasmine.objectContaining({ theme: 'dark', itemsPerPage: 50, autoArchive: true,
        contactRetentionYears: 3 }));
      expect(component.retentionYears).toEqual([0, 1, 2, 3, 5]);
      expect(bed.notifications.success).toHaveBeenCalledWith('profile.preferencesUpdated');
      expect(bed.theme.apply).toHaveBeenCalledWith('dark');
      expect(component.isUpdating).toBeFalse();
      expect(reload).not.toHaveBeenCalled();
    });

    it('switches to a newly preferred language with a page reload', fakeAsync(() => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = bed.create().componentInstance;
      const reload = spyOn(component, 'reloadPage');
      bed.users.updateUserPreferences.and.returnValue(of(preferences));
      component.preferencesForm.patchValue({ language: 'en' });

      component.onUpdatePreferences();

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
      tick(799);
      expect(reload).not.toHaveBeenCalled();
      tick(1);
      expect(reload).toHaveBeenCalledTimes(1);
    }));

    it('ignores a language the application does not offer', () => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = bed.create().componentInstance;
      bed.users.updateUserPreferences.and.returnValue(of(preferences));
      component.preferencesForm.patchValue({ language: 'de' });

      component.onUpdatePreferences();

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('fr');
    });

    it('does not save without a valid inactivity delay', () => {
      const component = bed.create().componentInstance;
      component.preferencesForm.patchValue({ inactivityDays: 0 });

      component.onUpdatePreferences();

      expect(bed.users.updateUserPreferences).not.toHaveBeenCalled();
    });

    it('keeps the follow-up delay read, and does not save one out of range', () => {
      const component = bed.create().componentInstance;
      expect(component.preferencesForm.value.followUpDays).toBe(5);

      for (const days of [0, 61, 2.5]) {
        component.preferencesForm.patchValue({ followUpDays: days });
        component.onUpdatePreferences();
      }

      expect(bed.users.updateUserPreferences).not.toHaveBeenCalled();
    });

    it('reports preferences that cannot be saved', () => {
      const component = bed.create().componentInstance;
      bed.users.updateUserPreferences.and.returnValue(throwError(() => new Error('offline')));

      component.onUpdatePreferences();

      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.updatingPreferences');
      expect(bed.theme.apply).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
    });
  });

  describe('data export', () => {
    it('downloads the exported data as JSON', () => {
      const component = bed.create().componentInstance;
      const blob = new Blob(['{}'], { type: 'application/json' });
      bed.users.exportUserData.and.returnValue(of(blob));
      const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:export');
      spyOn(URL, 'revokeObjectURL');
      const click = spyOn(HTMLAnchorElement.prototype, 'click');

      component.onExportData();

      expect(createUrl).toHaveBeenCalledWith(blob);
      expect(click).toHaveBeenCalled();
      expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe('indezy-user-data.json');
      expect(bed.notifications.success).toHaveBeenCalledWith('profile.dataExported');
    });

    it('reports an export that fails', () => {
      const component = bed.create().componentInstance;
      bed.users.exportUserData.and.returnValue(throwError(() => new Error('offline')));

      component.onExportData();

      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.exportingData');
    });

    it('exports from the account tab', async () => {
      const fixture = bed.create();
      bed.users.exportUserData.and.returnValue(throwError(() => new Error('offline')));
      const element = fixture.nativeElement as HTMLElement;
      Array.from(element.querySelectorAll<HTMLElement>('[role="tab"]'))
        .find(tab => tab.textContent!.includes('profile.tabs.account'))!.click();
      for (let attempt = 0; attempt < 50 && !element.querySelector('mat-tab-body.mat-mdc-tab-body-active')?.textContent!
        .includes('common.export'); attempt++) {
        fixture.detectChanges();
        await fixture.whenStable();
        await new Promise(resolve => setTimeout(resolve, 10));
      }

      const button = Array.from(element.querySelectorAll<HTMLButtonElement>('mat-tab-body.mat-mdc-tab-body-active button'))
        .find(candidate => candidate.textContent!.includes('common.export'))!;
      button.click();

      expect(bed.users.exportUserData).toHaveBeenCalled();
    });
  });
});

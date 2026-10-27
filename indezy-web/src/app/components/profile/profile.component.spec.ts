import { Subject, of, throwError } from 'rxjs';
import { UserPreferences, UserProfile } from '../../models/user-management.models';
import { ProfileComponent } from './profile.component';
import { profileTestBed, profile, notificationSettings } from './profile-test-bed.spec';

describe('ProfileComponent', () => {
  const bed = profileTestBed();

  describe('loading', () => {
    it('fills every form from the profile, its preferences and its notification settings', () => {
      const component = bed.create().componentInstance;

      expect(component.userProfile).toEqual(profile);
      expect(component.profileForm.value).toEqual(jasmine.objectContaining({
        firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', city: 'Paris', position: 'Tech lead',
        skills: ['Java', 'Angular'], languages: ['Anglais'], targetDailyRate: 650
      }));
      expect(component.profileForm.value.birthDate).toEqual(new Date(1990, 4, 12));
      expect(component.profileForm.value.availabilityDate).toEqual(new Date(2026, 10, 2));
      expect(component.preferencesForm.value).toEqual(jasmine.objectContaining({
        theme: 'dark', dateFormat: 'DD/MM/YYYY', itemsPerPage: 50, defaultView: 'projects', autoSave: false, inactivityDays: 30
      }));
      expect(component.notificationForm.value).toEqual(notificationSettings);
      expect(component.isLoading).toBeFalse();
    });

    it('defaults missing preferences and profile lists', () => {
      const component = bed.create(of({ id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }),
        { theme: 'light', language: 'fr' } as UserPreferences).componentInstance;

      expect(component.profileForm.value).toEqual(jasmine.objectContaining({
        birthDate: null, availabilityDate: null, targetDailyRate: null, skills: [], languages: []
      }));
      expect(component.preferencesForm.value.dateFormat).toBe('DD/MM/YYYY');
      expect(component.preferencesForm.value.itemsPerPage).toBe(25);
    });

    it('shows a spinner until the profile arrives, then the personal information tab', () => {
      const loaded = new Subject<UserProfile>();
      const fixture = bed.create(loaded);

      expect(bed.text(fixture)).toContain('profile.loading');
      expect(fixture.nativeElement.querySelector('app-profile-personal-info')).toBeNull();

      loaded.next(profile);

      expect(bed.text(fixture)).not.toContain('profile.loading');
      expect(bed.text(fixture)).toContain('Ada Lovelace');
      expect(bed.text(fixture)).toContain('profile.tabs.personalInfo');
    });

    it('reports a profile that cannot be loaded', () => {
      const fixture = bed.create(throwError(() => new Error('offline')));

      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.loadingProfile');
      expect(bed.consoleError).toHaveBeenCalled();
      expect(fixture.componentInstance.isLoading).toBeFalse();
      expect(fixture.nativeElement.querySelector('.profile-content')).toBeNull();
    });

    it('reads whether two-factor authentication is on, and keeps it off when that fails', () => {
      const component = bed.create().componentInstance;
      expect(component.twoFactorEnabled).toBeFalse();

      bed.users.getSecuritySettings.and.returnValue(of({ twoFactorEnabled: true, lastPasswordChange: new Date() }));
      component.ngOnInit();
      expect(component.twoFactorEnabled).toBeTrue();

      component.twoFactorEnabled = false;
      bed.users.getSecuritySettings.and.returnValue(throwError(() => new Error('offline')));
      component.ngOnInit();
      expect(component.twoFactorEnabled).toBeFalse();
    });
  });

  describe('personal information', () => {
    it('saves the profile with ISO dates and confirms when the suggestion inputs did not change', () => {
      const component = bed.create().componentInstance;
      bed.users.updateUserProfile.and.returnValue(of({ ...profile, skills: ['Angular', 'Java'], phone: '0700000000' }));
      // The datepicker gives local midnight, whatever the time zone.
      component.profileForm.patchValue({ phone: '0700000000', birthDate: new Date(1990, 4, 12), availabilityDate: new Date(2026, 11, 1) });

      component.onUpdateProfile();

      expect(bed.users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({
        phone: '0700000000', birthDate: '1990-05-12', availabilityDate: '2026-12-01'
      }));
      expect(bed.notifications.success).toHaveBeenCalledWith('profile.updateSuccess');
      expect(bed.notifications.successWithAction).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
      expect(component.userProfile!.phone).toBe('0700000000');
    });

    it('sends empty dates as null', () => {
      const component = bed.create().componentInstance;
      bed.users.updateUserProfile.and.returnValue(of(profile));
      component.profileForm.patchValue({ birthDate: null, availabilityDate: null });

      component.onUpdateProfile();

      expect(bed.users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({ birthDate: null, availabilityDate: null }));
    });

    it('offers to see the new search suggestions when the job title changed', () => {
      const component = bed.create().componentInstance;
      const action = new Subject<void>();
      bed.notifications.successWithAction.and.returnValue(action);
      bed.users.updateUserProfile.and.returnValue(of({ ...profile, position: 'Architect' }));

      component.onUpdateProfile();

      expect(bed.notifications.successWithAction).toHaveBeenCalledWith('profile.updateSuccessSuggestions', 'profile.seeSuggestions', 8000);
      expect(bed.notifications.success).not.toHaveBeenCalled();
      expect(bed.navigate).not.toHaveBeenCalled();

      action.next();
      expect(bed.navigate).toHaveBeenCalledWith(['/offers'], { state: { openSuggestions: true } });
    });

    it('treats a changed city or daily rate as new suggestion inputs', () => {
      const component = bed.create().componentInstance;
      bed.notifications.successWithAction.and.returnValue(new Subject<void>());

      bed.users.updateUserProfile.and.returnValue(of({ ...profile, city: 'Lyon' }));
      component.onUpdateProfile();
      bed.users.updateUserProfile.and.returnValue(of({ ...profile, city: 'Lyon', targetDailyRate: 700 }));
      component.onUpdateProfile();

      expect(bed.notifications.successWithAction).toHaveBeenCalledTimes(2);
    });

    it('does not save an invalid profile', () => {
      const component = bed.create().componentInstance;
      component.profileForm.patchValue({ email: 'not-an-email' });

      component.onUpdateProfile();

      expect(bed.users.updateUserProfile).not.toHaveBeenCalled();
    });

    it('reports a profile that cannot be saved', () => {
      const component = bed.create().componentInstance;
      bed.users.updateUserProfile.and.returnValue(throwError(() => new Error('offline')));

      component.onUpdateProfile();

      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.updatingProfile');
      expect(component.isUpdating).toBeFalse();
    });

    it('saves when the personal information form is submitted', () => {
      const fixture = bed.create();
      bed.users.updateUserProfile.and.returnValue(of(profile));

      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('app-profile-personal-info button[type="submit"]')!.click();

      expect(bed.users.updateUserProfile).toHaveBeenCalled();
    });
  });

  describe('avatar', () => {
    function upload(component: ProfileComponent, file?: File): HTMLInputElement {
      const input = document.createElement('input');
      input.type = 'file';
      if (file) {
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
      }
      component.onAvatarUpload({ target: input } as unknown as Event);
      return input;
    }

    it('ignores an empty selection', () => {
      const component = bed.create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage');

      upload(component);

      expect(downscale).not.toHaveBeenCalled();
      expect(bed.notifications.error).not.toHaveBeenCalled();
    });

    it('refuses files that are not images or are too large', () => {
      const component = bed.create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage');

      const input = upload(component, new File(['x'], 'cv.pdf', { type: 'application/pdf' }));
      expect(input.value).toBe('');
      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.selectImage');

      const large = new File(['x'], 'photo.png', { type: 'image/png' });
      Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });
      upload(component, large);
      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.imageTooLarge');
      expect(downscale).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
    });

    it('uploads the downscaled image and shows it', async () => {
      const component = bed.create().componentInstance;
      const scaled = new Blob(['jpeg'], { type: 'image/jpeg' });
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(scaled);
      bed.users.uploadAvatar.and.returnValue(of('data:image/jpeg;base64,AAAA'));
      const file = new File(['png'], 'photo.png', { type: 'image/png' });

      upload(component, file);
      expect(component.isUpdating).toBeTrue();
      await downscale.calls.mostRecent().returnValue;

      expect(downscale).toHaveBeenCalledWith(file, ProfileComponent.AVATAR_SIZE);
      expect(bed.users.uploadAvatar).toHaveBeenCalledWith(scaled);
      expect(component.userProfile!.avatar).toBe('data:image/jpeg;base64,AAAA');
      expect(component.isUpdating).toBeFalse();
      expect(bed.notifications.success).toHaveBeenCalledWith('profile.avatarUpdated');
    });

    it('confirms the upload even before a profile was loaded', async () => {
      const component = bed.create(throwError(() => new Error('offline'))).componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(new Blob(['jpeg']));
      bed.users.uploadAvatar.and.returnValue(of('data:image/jpeg;base64,AAAA'));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await downscale.calls.mostRecent().returnValue;

      expect(component.userProfile).toBeNull();
      expect(bed.notifications.success).toHaveBeenCalledWith('profile.avatarUpdated');
    });

    it('reports an upload that fails', async () => {
      const component = bed.create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(new Blob(['jpeg']));
      bed.users.uploadAvatar.and.returnValue(throwError(() => new Error('too large')));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await downscale.calls.mostRecent().returnValue;

      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.uploadingAvatar');
      expect(component.isUpdating).toBeFalse();
      expect(component.userProfile!.avatar).toBeUndefined();
    });

    it('reports an image that cannot be read', async () => {
      const component = bed.create().componentInstance;
      spyOn(ProfileComponent, 'downscaleImage').and.rejectWith(new Error('Unreadable image'));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await new Promise(resolve => setTimeout(resolve));

      expect(bed.users.uploadAvatar).not.toHaveBeenCalled();
      expect(bed.notifications.error).toHaveBeenCalledWith('profile.errors.selectImage');
      expect(component.isUpdating).toBeFalse();
    });

    it('center-crops an image to a small JPEG square', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 40;
      canvas.height = 20;
      canvas.getContext('2d')!.fillRect(0, 0, 40, 20);
      const png = await new Promise<Blob>(resolve => canvas.toBlob(blob => resolve(blob!), 'image/png'));

      const scaled = await ProfileComponent.downscaleImage(png, 16);

      expect(scaled.type).toBe('image/jpeg');
      const bitmap = await createImageBitmap(scaled);
      expect([bitmap.width, bitmap.height]).toEqual([16, 16]);
    });

    it('rejects data that is not an image', async () => {
      await expectAsync(ProfileComponent.downscaleImage(new Blob(['not an image'], { type: 'image/png' }), 16))
        .toBeRejectedWithError('Unreadable image');
    });
  });
});

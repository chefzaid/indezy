import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';

import { AbstractControlOptions, ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { UserManagementService, TwoFactorSetup } from '../../services/user-management/user-management.service';
import { UserProfile, UserPreferences, UserNotificationSettings, PasswordChangeRequest } from '../../models/user-management.models';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../services/notification/notification.service';
import { ProfilePersonalInfoComponent } from './personal-info/profile-personal-info.component';
import { LANGUAGE_STORAGE_KEY, getSavedLanguage } from '../../shared/locale/app-locale';
import { ThemeService } from '../../shared/theme/theme.service';

@Component({
    selector: 'app-profile',
    imports: [
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    TranslateModule,
    ProfilePersonalInfoComponent
],
    templateUrl: './profile.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  /** Avatars are stored inline, so photos are cropped to a small square before upload. */
  static readonly AVATAR_SIZE = 256;

  private readonly themeService = inject(ThemeService);

  userProfile: UserProfile | null = null;
  profileForm!: FormGroup;
  passwordForm!: FormGroup;
  preferencesForm!: FormGroup;
  notificationForm!: FormGroup;

  isLoading = false;
  isUpdating = false;
  selectedTabIndex = 0;

  twoFactorEnabled = false;
  twoFactorSetup: TwoFactorSetup | null = null;
  twoFactorCode = '';
  isProcessingTwoFactor = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly userManagementService: UserManagementService,
    private readonly notificationService: NotificationService
  ) {
    this.initializeForms();
  }

  ngOnInit(): void {
    this.loadUserProfile();
    this.loadSecuritySettings();
  }

  private loadSecuritySettings(): void {
    this.userManagementService.getSecuritySettings().subscribe({
      next: settings => this.twoFactorEnabled = settings.twoFactorEnabled,
      error: () => { /* leave 2FA state at its default when settings can't be loaded */ }
    });
  }

  /** Begins 2FA setup: fetches a secret and shows the code-entry step. */
  startTwoFactorSetup(): void {
    this.isProcessingTwoFactor = true;
    this.userManagementService.enableTwoFactor().subscribe({
      next: setup => {
        this.twoFactorSetup = setup;
        this.twoFactorCode = '';
        this.isProcessingTwoFactor = false;
      },
      error: () => {
        this.isProcessingTwoFactor = false;
        this.notificationService.error('profile.twoFactor.error');
      }
    });
  }

  /** Confirms setup by verifying the entered code; activates 2FA on success. */
  verifyTwoFactorSetup(): void {
    if (!this.twoFactorCode.trim()) {
      return;
    }
    this.isProcessingTwoFactor = true;
    this.userManagementService.verifyTwoFactor(this.twoFactorCode.trim()).subscribe({
      next: success => {
        this.isProcessingTwoFactor = false;
        if (success) {
          this.twoFactorEnabled = true;
          this.twoFactorSetup = null;
          this.twoFactorCode = '';
          this.notificationService.success('profile.twoFactor.enabled');
        } else {
          this.notificationService.error('profile.twoFactor.invalidCode');
        }
      },
      error: () => {
        this.isProcessingTwoFactor = false;
        this.notificationService.error('profile.twoFactor.error');
      }
    });
  }

  /** Disables 2FA after validating a current code. */
  disableTwoFactor(): void {
    if (!this.twoFactorCode.trim()) {
      return;
    }
    this.isProcessingTwoFactor = true;
    this.userManagementService.disableTwoFactor(this.twoFactorCode.trim()).subscribe({
      next: () => {
        this.isProcessingTwoFactor = false;
        this.twoFactorEnabled = false;
        this.twoFactorCode = '';
        this.notificationService.success('profile.twoFactor.disabled');
      },
      error: () => {
        this.isProcessingTwoFactor = false;
        this.notificationService.error('profile.twoFactor.invalidCode');
      }
    });
  }

  cancelTwoFactorSetup(): void {
    this.twoFactorSetup = null;
    this.twoFactorCode = '';
  }

  private initializeForms(): void {
    this.profileForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      birthDate: [''],
      address: [''],
      city: [''],
      bio: ['', [Validators.maxLength(500)]],
      company: [''],
      position: [''],
      website: [''],
      linkedin: [''],
      github: [''],
      skills: [[]],
      languages: [[]],
      timezone: ['Europe/Paris'],
      currency: ['EUR']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator } as AbstractControlOptions);

    this.preferencesForm = this.fb.group({
      theme: ['light'],
      language: ['fr'],
      dateFormat: ['DD/MM/YYYY'],
      timeFormat: ['24h'],
      currency: ['EUR'],
      timezone: ['Europe/Paris'],
      defaultView: ['dashboard'],
      itemsPerPage: [25],
      autoSave: [true]
    });

    this.notificationForm = this.fb.group({
      emailNotifications: [true],
      pushNotifications: [true],
      projectUpdates: [true],
      clientMessages: [true],
      systemAlerts: [true],
      weeklyReports: [false],
      marketingEmails: [false]
    });
  }

  private passwordMatchValidator(group: FormGroup): { passwordMismatch: boolean } | null {
    const newPassword = group.get('newPassword');
    const confirmPassword = group.get('confirmPassword');

    if (newPassword && confirmPassword && newPassword.value !== confirmPassword.value) {
      confirmPassword.setErrors({ passwordMismatch: true });
      return { passwordMismatch: true };
    }

    if (confirmPassword?.hasError('passwordMismatch')) {
      delete confirmPassword.errors!['passwordMismatch'];
      if (Object.keys(confirmPassword.errors!).length === 0) {
        confirmPassword.setErrors(null);
      }
    }

    return null;
  }

  private loadUserProfile(): void {
    this.isLoading = true;

    this.userManagementService.getUserProfile().subscribe({
      next: (profile: UserProfile) => {
        this.userProfile = profile;
        this.populateForms(profile);
        this.isLoading = false;
      },
      error: (error: unknown) => {
        console.error('Error loading profile:', error);
        this.notificationService.error('profile.errors.loadingProfile');
        this.isLoading = false;
      }
    });
  }

  private populateForms(profile: UserProfile): void {
    this.profileForm.patchValue({
      firstName: profile.firstName,
      lastName: profile.lastName,
      email: profile.email,
      phone: profile.phone,
      birthDate: profile.birthDate ? new Date(profile.birthDate) : null,
      address: profile.address,
      city: profile.city,
      bio: profile.bio,
      company: profile.company,
      position: profile.position,
      website: profile.website,
      linkedin: profile.linkedin,
      github: profile.github,
      skills: profile.skills || [],
      languages: profile.languages || [],
      timezone: profile.timezone,
      currency: profile.currency
    });

    // Load preferences
    this.userManagementService.getUserPreferences().subscribe((preferences: UserPreferences) => {
      this.preferencesForm.patchValue({
        ...preferences,
        // Stored patterns may use Java casing (dd/MM/yyyy); the selector lists DD/MM/YYYY.
        dateFormat: preferences.dateFormat?.toUpperCase() ?? 'DD/MM/YYYY',
        itemsPerPage: preferences.itemsPerPage !== undefined && preferences.itemsPerPage !== null ? Number(preferences.itemsPerPage) : 25
      });
    });

    // Load notification settings
    this.userManagementService.getNotificationSettings().subscribe((settings: UserNotificationSettings) => {
      this.notificationForm.patchValue(settings);
    });
  }

  onUpdateProfile(): void {
    if (this.profileForm.valid) {
      this.isUpdating = true;
      const formValue = this.profileForm.value;

      const updatedProfile: Partial<UserProfile> = {
        ...formValue,
        birthDate: formValue.birthDate ? formValue.birthDate.toISOString().split('T')[0] : null
      };

      this.userManagementService.updateUserProfile(updatedProfile).subscribe({
        next: (profile: UserProfile) => {
          this.userProfile = profile;
          this.isUpdating = false;
          this.notificationService.success('profile.updateSuccess');
        },
        error: (error: unknown) => {
          console.error('Error updating profile:', error);
          this.isUpdating = false;
          this.notificationService.error('profile.errors.updatingProfile');
        }
      });
    }
  }

  onChangePassword(): void {
    if (this.passwordForm.valid) {
      this.isUpdating = true;
      const passwordData: PasswordChangeRequest = this.passwordForm.value;

      this.userManagementService.changePassword(passwordData).subscribe({
        next: () => {
          this.isUpdating = false;
          this.passwordForm.reset();
          this.notificationService.success('profile.passwordChanged');
        },
        error: (error: unknown) => {
          console.error('Error changing password:', error);
          this.isUpdating = false;
          this.notificationService.error('profile.errors.changingPassword');
        }
      });
    }
  }

  onUpdatePreferences(): void {
    if (this.preferencesForm.valid) {
      this.isUpdating = true;
      const preferences: Partial<UserPreferences> = this.preferencesForm.value;

      this.userManagementService.updateUserPreferences(preferences).subscribe({
        next: () => {
          this.isUpdating = false;
          this.notificationService.success('profile.preferencesUpdated');
          this.themeService.apply(preferences.theme);
          const language = preferences.language;
          if ((language === 'fr' || language === 'en') && language !== getSavedLanguage()) {
            // Apply the preferred language like the toolbar switch does (formats need a reload).
            localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
            setTimeout(() => this.reloadPage(), 800);
          }
        },
        error: (error: unknown) => {
          console.error('Error updating preferences:', error);
          this.isUpdating = false;
          this.notificationService.error('profile.errors.updatingPreferences');
        }
      });
    }
  }

  /** Separated so tests can stub the full page reload. */
  reloadPage(): void {
    window.location.reload();
  }

  onUpdateNotifications(): void {
    if (this.notificationForm.valid) {
      this.isUpdating = true;
      const settings: Partial<UserNotificationSettings> = this.notificationForm.value;

      this.userManagementService.updateNotificationSettings(settings).subscribe({
        next: () => {
          this.isUpdating = false;
          this.notificationService.success('profile.notificationsUpdated');
        },
        error: (error: unknown) => {
          console.error('Error updating notifications:', error);
          this.isUpdating = false;
          this.notificationService.error('profile.errors.updatingNotifications');
        }
      });
    }
  }

  onAvatarUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.notificationService.error('profile.errors.selectImage');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.notificationService.error('profile.errors.imageTooLarge');
      return;
    }

    this.isUpdating = true;
    ProfileComponent.downscaleImage(file, ProfileComponent.AVATAR_SIZE)
      .then(blob => {
        this.userManagementService.uploadAvatar(blob).subscribe({
          next: (avatarUrl: string) => {
            if (this.userProfile) {
              this.userProfile = { ...this.userProfile, avatar: avatarUrl };
            }
            this.isUpdating = false;
            this.notificationService.success('profile.avatarUpdated');
          },
          error: (error: unknown) => {
            console.error('Error uploading avatar:', error);
            this.isUpdating = false;
            this.notificationService.error('profile.errors.uploadingAvatar');
          }
        });
      })
      .catch(() => {
        this.isUpdating = false;
        this.notificationService.error('profile.errors.selectImage');
      });
  }

  /** Center-crops an image to a square and scales it down to size x size pixels (JPEG). */
  static downscaleImage(file: Blob, size: number): Promise<Blob> {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = (): void => {
        const side = Math.min(image.naturalWidth, image.naturalHeight);
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const context = canvas.getContext('2d');
        if (!context || side === 0) {
          URL.revokeObjectURL(url);
          reject(new Error('Unreadable image'));
          return;
        }
        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, size, size);
        context.drawImage(image,
          (image.naturalWidth - side) / 2, (image.naturalHeight - side) / 2, side, side,
          0, 0, size, size);
        URL.revokeObjectURL(url);
        canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Encoding failed')), 'image/jpeg', 0.85);
      };
      image.onerror = (): void => {
        URL.revokeObjectURL(url);
        reject(new Error('Unreadable image'));
      };
      image.src = url;
    });
  }

  onExportData(): void {
    this.userManagementService.exportUserData().subscribe({
      next: (blob: Blob) => {
        const url = globalThis.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'indezy-user-data.json';
        link.click();
        globalThis.URL.revokeObjectURL(url);

        this.notificationService.success('profile.dataExported');
      },
      error: (error: unknown) => {
        console.error('Error exporting data:', error);
        this.notificationService.error('profile.errors.exportingData');
      }
    });
  }

}

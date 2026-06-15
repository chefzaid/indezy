import { Component, OnInit } from '@angular/core';

import { AbstractControlOptions, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
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
import { UserManagementService } from '../../services/user-management/user-management.service';
import { UserProfile, UserPreferences, UserNotificationSettings, PasswordChangeRequest } from '../../models/user-management.models';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../services/notification/notification.service';
import { ProfilePersonalInfoComponent } from './personal-info/profile-personal-info.component';

@Component({
    selector: 'app-profile',
    imports: [
    ReactiveFormsModule,
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
    styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  userProfile: UserProfile | null = null;
  profileForm!: FormGroup;
  passwordForm!: FormGroup;
  preferencesForm!: FormGroup;
  notificationForm!: FormGroup;

  isLoading = false;
  isUpdating = false;
  selectedTabIndex = 0;

  constructor(
    private readonly fb: FormBuilder,
    private readonly userManagementService: UserManagementService,
    private readonly notificationService: NotificationService
  ) {
    this.initializeForms();
  }

  ngOnInit(): void {
    this.loadUserProfile();
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
      this.preferencesForm.patchValue(preferences);
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
        },
        error: (error: unknown) => {
          console.error('Error updating preferences:', error);
          this.isUpdating = false;
          this.notificationService.error('profile.errors.updatingPreferences');
        }
      });
    }
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
    if (input.files?.[0]) {
      const file = input.files[0];

      // Validate file type and size
      if (!file.type.startsWith('image/')) {
        this.notificationService.error('profile.errors.selectImage');
        return;
      }

      if (file.size > 5 * 1024 * 1024) { // 5MB limit
        this.notificationService.error('profile.errors.imageTooLarge');
        return;
      }

      this.isUpdating = true;
      this.userManagementService.uploadAvatar(file).subscribe({
        next: (avatarUrl: string) => {
          if (this.userProfile) {
            this.userProfile.avatar = avatarUrl;
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
    }
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

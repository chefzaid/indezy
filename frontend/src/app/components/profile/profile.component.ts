import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { UserManagementService, UserProfile, UserPreferences, UserNotificationSettings, PasswordChangeRequest } from '../../services/user-management.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatChipsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatListModule,
    MatDialogModule
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

  availableSkills = [
    'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'C#', 'PHP',
    'PostgreSQL', 'MongoDB', 'MySQL', 'Docker', 'Kubernetes', 'AWS', 'Azure',
    'TypeScript', 'JavaScript', 'HTML/CSS', 'Git', 'Jenkins', 'GraphQL'
  ];

  availableLanguages = [
    'Français', 'Anglais', 'Espagnol', 'Allemand', 'Italien', 'Portugais',
    'Chinois', 'Japonais', 'Arabe', 'Russe', 'Néerlandais'
  ];

  timezones = [
    'Europe/Paris', 'Europe/London', 'Europe/Berlin', 'Europe/Madrid',
    'America/New_York', 'America/Los_Angeles', 'Asia/Tokyo', 'Asia/Shanghai'
  ];

  currencies = [
    { value: 'EUR', label: '€ Euro' },
    { value: 'USD', label: '$ Dollar US' },
    { value: 'GBP', label: '£ Livre Sterling' },
    { value: 'CHF', label: 'CHF Franc Suisse' }
  ];

  constructor(
    private fb: FormBuilder,
    private userManagementService: UserManagementService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
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
    }, { validators: this.passwordMatchValidator });

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

  private passwordMatchValidator(group: FormGroup) {
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
      next: (profile) => {
        this.userProfile = profile;
        this.populateForms(profile);
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading profile:', error);
        this.snackBar.open('Erreur lors du chargement du profil', 'Fermer', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
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
    this.userManagementService.getUserPreferences().subscribe(preferences => {
      this.preferencesForm.patchValue(preferences);
    });

    // Load notification settings
    this.userManagementService.getNotificationSettings().subscribe(settings => {
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
        next: (profile) => {
          this.userProfile = profile;
          this.isUpdating = false;
          this.snackBar.open('Profil mis à jour avec succès', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          console.error('Error updating profile:', error);
          this.isUpdating = false;
          this.snackBar.open('Erreur lors de la mise à jour du profil', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
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
          this.snackBar.open('Mot de passe modifié avec succès', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          console.error('Error changing password:', error);
          this.isUpdating = false;
          this.snackBar.open('Erreur lors du changement de mot de passe', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
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
          this.snackBar.open('Préférences mises à jour', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          console.error('Error updating preferences:', error);
          this.isUpdating = false;
          this.snackBar.open('Erreur lors de la mise à jour des préférences', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
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
          this.snackBar.open('Paramètres de notification mis à jour', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          console.error('Error updating notifications:', error);
          this.isUpdating = false;
          this.snackBar.open('Erreur lors de la mise à jour des notifications', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
        }
      });
    }
  }

  onAvatarUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];

      // Validate file type and size
      if (!file.type.startsWith('image/')) {
        this.snackBar.open('Veuillez sélectionner une image', 'Fermer', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
        return;
      }

      if (file.size > 5 * 1024 * 1024) { // 5MB limit
        this.snackBar.open('L\'image ne doit pas dépasser 5MB', 'Fermer', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
        return;
      }

      this.isUpdating = true;
      this.userManagementService.uploadAvatar(file).subscribe({
        next: (avatarUrl) => {
          if (this.userProfile) {
            this.userProfile.avatar = avatarUrl;
          }
          this.isUpdating = false;
          this.snackBar.open('Avatar mis à jour', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          console.error('Error uploading avatar:', error);
          this.isUpdating = false;
          this.snackBar.open('Erreur lors du téléchargement de l\'avatar', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
        }
      });
    }
  }

  onExportData(): void {
    this.userManagementService.exportUserData().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'indezy-user-data.json';
        link.click();
        window.URL.revokeObjectURL(url);

        this.snackBar.open('Données exportées avec succès', 'Fermer', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        console.error('Error exporting data:', error);
        this.snackBar.open('Erreur lors de l\'export des données', 'Fermer', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  removeSkill(skill: string): void {
    const skills = this.profileForm.get('skills')?.value || [];
    const index = skills.indexOf(skill);
    if (index >= 0) {
      skills.splice(index, 1);
      this.profileForm.get('skills')?.setValue([...skills]);
    }
  }

  addSkill(skill: string): void {
    const skills = this.profileForm.get('skills')?.value || [];
    if (!skills.includes(skill)) {
      skills.push(skill);
      this.profileForm.get('skills')?.setValue([...skills]);
    }
  }

  removeLanguage(language: string): void {
    const languages = this.profileForm.get('languages')?.value || [];
    const index = languages.indexOf(language);
    if (index >= 0) {
      languages.splice(index, 1);
      this.profileForm.get('languages')?.setValue([...languages]);
    }
  }

  addLanguage(language: string): void {
    const languages = this.profileForm.get('languages')?.value || [];
    if (!languages.includes(language)) {
      languages.push(language);
      this.profileForm.get('languages')?.setValue([...languages]);
    }
  }
}

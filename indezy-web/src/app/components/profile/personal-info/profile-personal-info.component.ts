import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ReactiveFormsModule, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { UserProfile } from '../../../models/user-management.models';
import { ProfileSkillsEditorComponent } from '../skills-editor/profile-skills-editor.component';
import { CvImportData, ProfileCvImportDialogComponent } from '../cv-import/profile-cv-import-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { PROFILE_LANGUAGES } from '../profile-languages';

/**
 * "Personal information" tab of the profile page: avatar, basic/professional
 * details, skills, languages and regional settings. The form group is owned by
 * the parent ProfileComponent; saving and avatar upload are delegated to it.
 */
@Component({
    selector: 'app-profile-personal-info',
    imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatDatepickerModule,
    MatProgressSpinnerModule,
    TranslateModule,
    ProfileSkillsEditorComponent
],
    templateUrl: './profile-personal-info.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['../profile.component.scss']
})
export class ProfilePersonalInfoComponent {
  /** The parts of the offers' match score the profile weighs; the skills always count. */
  readonly matchParts = [
    { control: 'matchWeightSkills', label: 'skills', optional: false },
    { control: 'matchWeightRate', label: 'rate', optional: true },
    { control: 'matchWeightPlace', label: 'place', optional: true },
    { control: 'matchWeightStart', label: 'start', optional: true }
  ] as const;

  @Input({ required: true }) form!: FormGroup;
  @Input({ required: true }) userProfile!: UserProfile;
  @Input() isUpdating = false;

  @Output() save = new EventEmitter<void>();
  @Output() avatarUpload = new EventEmitter<Event>();

  readonly defaultAvatar = 'assets/images/default-avatar.svg';
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  /** Fills the form from a CV read with AI, once what to take is reviewed; saving stays the member's. */
  importCv(): void {
    const data: CvImportData = { current: this.form.getRawValue(), languages: this.availableLanguages };
    this.dialog.open(ProfileCvImportDialogComponent, { data, width: '640px', maxWidth: '96vw', autoFocus: false }).afterClosed()
      .subscribe(patch => {
        if (patch && Object.keys(patch).length) {
          this.form.patchValue(patch);
          this.form.markAsDirty();
          this.notifications.info('profile.cvImport.filled');
        }
      });
  }

  /** Falls back to the default avatar when the stored image cannot be loaded. */
  onAvatarError(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith(this.defaultAvatar)) {
      image.src = this.defaultAvatar;
    }
  }

  availableLanguages = [...PROFILE_LANGUAGES];

  addLanguage(language: string): void {
    const languages: string[] = this.form.get('languages')?.value || [];
    if (!languages.includes(language)) {
      this.form.get('languages')?.setValue([...languages, language]);
    }
  }

  removeLanguage(language: string): void {
    const languages: string[] = this.form.get('languages')?.value || [];
    if (languages.includes(language)) {
      this.form.get('languages')?.setValue(languages.filter(l => l !== language));
    }
  }
}

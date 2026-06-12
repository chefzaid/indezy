import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { UserProfile } from '../../../models/user-management.models';

/**
 * "Personal information" tab of the profile page: avatar, basic/professional
 * details, skills, languages and regional settings. The form group is owned by
 * the parent ProfileComponent; saving and avatar upload are delegated to it.
 */
@Component({
    selector: 'app-profile-personal-info',
    imports: [
        CommonModule,
        ReactiveFormsModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatChipsModule,
        MatDatepickerModule,
        MatNativeDateModule,
        MatProgressSpinnerModule,
        TranslateModule
    ],
    templateUrl: './profile-personal-info.component.html',
    styleUrls: ['../profile.component.scss']
})
export class ProfilePersonalInfoComponent {
  @Input({ required: true }) form!: FormGroup;
  @Input({ required: true }) userProfile!: UserProfile;
  @Input() isUpdating = false;

  @Output() save = new EventEmitter<void>();
  @Output() avatarUpload = new EventEmitter<Event>();

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

  addSkill(skill: string): void {
    const skills: string[] = this.form.get('skills')?.value || [];
    if (!skills.includes(skill)) {
      this.form.get('skills')?.setValue([...skills, skill]);
    }
  }

  removeSkill(skill: string): void {
    const skills: string[] = this.form.get('skills')?.value || [];
    if (skills.includes(skill)) {
      this.form.get('skills')?.setValue(skills.filter(s => s !== skill));
    }
  }

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

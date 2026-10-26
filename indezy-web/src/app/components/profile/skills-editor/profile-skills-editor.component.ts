import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { SkillDetail, SkillLevel } from '../../../models/user-management.models';

/** Skills offered while typing; any other can be written. */
export const SUGGESTED_SKILLS = [
  'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'Spring Boot', 'Kotlin', 'C#', '.NET', 'PHP', 'Go',
  'PostgreSQL', 'MongoDB', 'MySQL', 'Kafka', 'Docker', 'Kubernetes', 'Terraform', 'AWS', 'Azure', 'GCP',
  'TypeScript', 'JavaScript', 'HTML/CSS', 'Git', 'GitLab CI', 'GraphQL', 'Scrum'
];
export const SKILL_LEVELS: SkillLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
const SKILL_MAX = 60;
const SKILLS_MAX = 50;

/**
 * The profile's skills, written freely or picked from suggestions, each with an optional level and years of
 * experience. It edits the parent form's {@code skills} (names, in order) and {@code skillDetails} (keyed by name).
 */
@Component({
  selector: 'app-profile-skills-editor',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './profile-skills-editor.component.html',
  styleUrls: ['./profile-skills-editor.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileSkillsEditorComponent {
  @Input({ required: true }) form!: FormGroup;

  readonly levels = SKILL_LEVELS;
  readonly skillMax = SKILL_MAX;
  readonly newSkill = new FormControl('', { nonNullable: true });

  get skills(): string[] {
    return this.form.get('skills')?.value ?? [];
  }

  get full(): boolean {
    return this.skills.length >= SKILLS_MAX;
  }

  /** The suggestions containing what is typed, without the skills already named. */
  get suggestions(): string[] {
    const typed = this.newSkill.value.trim().toLocaleLowerCase();
    return SUGGESTED_SKILLS.filter(skill => !this.has(skill) && skill.toLocaleLowerCase().includes(typed));
  }

  detail(skill: string): SkillDetail {
    return this.details()[skill] ?? {};
  }

  /** Adds the typed skill, or {@code skill}, unless it is empty, already named (case aside) or one too many. */
  add(skill: string = this.newSkill.value): void {
    const name = skill.trim().slice(0, SKILL_MAX);
    if (name && !this.has(name) && !this.full) {
      this.form.get('skills')!.setValue([...this.skills, name]);
      this.form.markAsDirty();
    }
    this.newSkill.setValue('');
  }

  remove(skill: string): void {
    const { [skill]: _removed, ...others } = this.details();
    this.form.patchValue({ skills: this.skills.filter(name => name !== skill), skillDetails: others });
    this.form.markAsDirty();
  }

  setLevel(skill: string, level: SkillLevel | null): void {
    this.setDetail(skill, { ...this.detail(skill), level: level ?? undefined });
  }

  /** Sets the years of experience: a whole number from 0 to 60, anything else clears them. */
  setYears(skill: string, value: string): void {
    const years = Number(value);
    const valid = value.trim() !== '' && Number.isInteger(years) && years >= 0 && years <= 60;
    this.setDetail(skill, { ...this.detail(skill), years: valid ? years : undefined });
  }

  private setDetail(skill: string, detail: SkillDetail): void {
    const kept: SkillDetail = {};
    if (detail.level) {
      kept.level = detail.level;
    }
    if (detail.years !== undefined) {
      kept.years = detail.years;
    }
    const { [skill]: _previous, ...others } = this.details();
    this.form.get('skillDetails')!.setValue(Object.keys(kept).length ? { ...others, [skill]: kept } : others);
    this.form.markAsDirty();
  }

  private details(): Record<string, SkillDetail> {
    return this.form.get('skillDetails')?.value ?? {};
  }

  private has(skill: string): boolean {
    const wanted = skill.toLocaleLowerCase();
    return this.skills.some(name => name.toLocaleLowerCase() === wanted);
  }
}

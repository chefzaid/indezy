import { ChangeDetectionStrategy, Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { SkillDetail, SkillLevel } from '../../../models/user-management.models';
import { SkillNamesService } from '../../../services/skill/skill-names.service';
import { SkillNames, skillKey } from '../../../shared/utils/skill-names';

/** Skills offered while typing; any other can be written. */
export const SUGGESTED_SKILLS = [
  'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'Spring Boot', 'Kotlin', 'C#', '.NET', 'PHP', 'Go',
  'PostgreSQL', 'MongoDB', 'MySQL', 'Kafka', 'Docker', 'Kubernetes', 'Terraform', 'AWS', 'Azure', 'GCP',
  'TypeScript', 'JavaScript', 'HTML/CSS', 'Git', 'GitLab CI', 'GraphQL', 'Scrum'
];
export const SKILL_LEVELS: SkillLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
const SKILL_MAX = 60;
const SKILLS_MAX = 50;

/** Skills of the profile that are one skill, and the name to keep for them. */
export interface SkillMerge {
  canonical: string;
  skills: string[];
}

/**
 * The profile's skills, written freely or picked from suggestions, each with an optional level and years of
 * experience. It edits the parent form's {@code skills} (names, in order) and {@code skillDetails} (keyed by name).
 * Spellings of one skill ("JS", "JavaScript", "Spring-Boot", "SpringBoot") are one skill: adding another spelling says
 * so, and skills already listed twice, or under an alias, are offered to merge under the usual name.
 */
@Component({
  selector: 'app-profile-skills-editor',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './profile-skills-editor.component.html',
  styleUrls: ['./profile-skills-editor.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileSkillsEditorComponent implements OnInit {
  @Input({ required: true }) form!: FormGroup;

  private readonly skillNames = inject(SkillNamesService);
  private readonly destroyRef = inject(DestroyRef);
  private names = SkillNames.NONE;
  /** The skill already listed under another spelling, when the last one added was. */
  alreadyListed: string | null = null;

  readonly levels = SKILL_LEVELS;
  readonly skillMax = SKILL_MAX;
  readonly newSkill = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.skillNames.names$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(names => this.names = names);
  }

  get skills(): string[] {
    return this.form.get('skills')?.value ?? [];
  }

  get full(): boolean {
    return this.skills.length >= SKILLS_MAX;
  }

  /** The suggestions containing what is typed, or one of whose aliases it begins, without the skills already named. */
  get suggestions(): string[] {
    const typed = this.newSkill.value.trim();
    const candidates = [...SUGGESTED_SKILLS, ...this.names.known]
      .filter((skill, index, all) => all.findIndex(other => skillKey(other) === skillKey(skill)) === index);
    return candidates.filter(skill => !this.has(skill) &&
      (skill.toLocaleLowerCase().includes(typed.toLocaleLowerCase()) || this.names.startsSkill(skill, typed)));
  }

  /** Groups of listed skills that are one skill, or a skill listed under an alias, with the name to keep. */
  get merges(): SkillMerge[] {
    const groups = new Map<string, SkillMerge>();
    for (const skill of this.skills) {
      const canonical = this.names.canonical(skill);
      const key = skillKey(canonical);
      const group = groups.get(key) ?? { canonical, skills: [] };
      group.skills.push(skill);
      groups.set(key, group);
    }
    return [...groups.values()].filter(group => group.skills.length > 1 || group.skills[0] !== group.canonical);
  }

  /** Lists the group's skills once, under its usual name where the first was, with the most its details say. */
  merge(group: SkillMerge): void {
    const details = this.details();
    const merged = group.skills.map(skill => details[skill] ?? {});
    const levels = merged.map(detail => detail.level).filter((level): level is SkillLevel => !!level);
    const years = merged.map(detail => detail.years).filter((value): value is number => value !== undefined);
    const others = Object.fromEntries(Object.entries(details).filter(([skill]) => !group.skills.includes(skill)));
    const detail: SkillDetail = {};
    if (levels.length) {
      detail.level = levels.reduce((best, level) => SKILL_LEVELS.indexOf(level) > SKILL_LEVELS.indexOf(best) ? level : best);
    }
    if (years.length) {
      detail.years = Math.max(...years);
    }
    // The usual name takes the place of the group's first skill; the others leave the list.
    const skills = this.skills.flatMap(skill => !group.skills.includes(skill) ? [skill]
      : skill === group.skills[0] ? [group.canonical] : []);
    this.form.patchValue({ skills, skillDetails: Object.keys(detail).length ? { ...others, [group.canonical]: detail } : others });
    this.form.markAsDirty();
  }

  detail(skill: string): SkillDetail {
    return this.details()[skill] ?? {};
  }

  /** Adds the typed skill, or {@code skill}, unless it is empty, already named (in any spelling) or one too many. */
  add(skill: string = this.newSkill.value): void {
    const name = skill.trim().slice(0, SKILL_MAX);
    this.alreadyListed = name ? this.skills.find(listed => this.names.same(listed, name)) ?? null : null;
    if (name && !this.alreadyListed && !this.full) {
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
    return this.skills.some(name => this.names.same(name, skill));
  }
}

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ProfileSkillsEditorComponent, SUGGESTED_SKILLS } from './profile-skills-editor.component';

describe('ProfileSkillsEditorComponent', () => {
  let fixture: ComponentFixture<ProfileSkillsEditorComponent>;
  let component: ProfileSkillsEditorComponent;
  let form: FormGroup;

  function create(skills: string[] | null = [], skillDetails: Record<string, unknown> | null = {}): void {
    TestBed.configureTestingModule({ imports: [ProfileSkillsEditorComponent, TranslateModule.forRoot()] });
    form = new FormBuilder().group({ skills: [skills], skillDetails: [skillDetails] });
    fixture = TestBed.createComponent(ProfileSkillsEditorComponent);
    component = fixture.componentInstance;
    component.form = form;
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('adds a written skill, trimmed and cut to 60 characters, once whatever its case', () => {
    create(['Angular']);

    component.newSkill.setValue('  Rust  ');
    component.add();
    component.add('rust');
    component.add('ANGULAR');
    component.add('   ');
    component.add('x'.repeat(80));

    expect(form.value.skills).toEqual(['Angular', 'Rust', 'x'.repeat(60)]);
    expect(component.newSkill.value).toBe('');
    expect(form.dirty).toBe(true);
  });

  it('adds the skill typed when Enter is pressed', () => {
    create();
    const input = element().querySelector<HTMLInputElement>('.skill-input input')!;
    input.value = 'Elixir';
    input.dispatchEvent(new Event('input'));

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(form.value.skills).toEqual(['Elixir']);
  });

  it('stops at 50 skills and says so', () => {
    create(Array.from({ length: 50 }, (_, i) => `Skill ${i}`));

    component.add('One more');

    expect(form.value.skills.length).toBe(50);
    expect(component.full).toBe(true);
    expect(element().textContent).toContain('profile.skillsLimit');
  });

  it('suggests the known skills containing what is typed, without those already named', () => {
    create(['java']);

    component.newSkill.setValue('JA');

    expect(component.suggestions).toEqual(SUGGESTED_SKILLS.filter(s => s.toLowerCase().includes('ja') && s !== 'Java'));
    expect(component.suggestions).toContain('JavaScript');
  });

  it('keeps a level and years per skill, and drops a detail left empty', () => {
    create(['Java', 'Go']);

    component.setLevel('Java', 'EXPERT');
    component.setYears('Java', '12');
    component.setYears('Go', '3');
    expect(form.value.skillDetails).toEqual({ Java: { level: 'EXPERT', years: 12 }, Go: { years: 3 } });

    component.setYears('Java', '');
    component.setLevel('Go', null);
    component.setYears('Go', '2.5');
    expect(form.value.skillDetails).toEqual({ Java: { level: 'EXPERT' } });

    component.setYears('Java', '61');
    component.setYears('Java', '-1');
    component.setLevel('Java', null);
    expect(form.value.skillDetails).toEqual({});
  });

  it('removes a skill with its detail', () => {
    create(['Java', 'Go'], { Java: { level: 'ADVANCED', years: 4 }, Go: { years: 1 } });

    element().querySelector<HTMLButtonElement>('.skill-row button')!.click();

    expect(form.value.skills).toEqual(['Go']);
    expect(form.value.skillDetails).toEqual({ Go: { years: 1 } });
  });

  it('shows each skill with its years and an empty state otherwise', () => {
    create(['Java'], { Java: { years: 7 } });

    expect(element().querySelector('.skill-name')!.textContent!.trim()).toBe('Java');
    expect(element().querySelector<HTMLInputElement>('.skill-years input')!.value).toBe('7');
    expect(component.detail('Go')).toEqual({});

    form.patchValue({ skills: [] });
    fixture.detectChanges();
    expect(element().textContent).toContain('profile.noSkills');
  });

  it('treats missing skills and details as empty', () => {
    create(null, null);

    expect(component.skills).toEqual([]);
    component.setLevel('Java', 'BEGINNER');
    expect(form.value.skillDetails).toEqual({ Java: { level: 'BEGINNER' } });
    component.add('Java');
    expect(form.value.skills).toEqual(['Java']);
  });

  it('sets the years typed in a row', () => {
    create(['Java']);
    const input = element().querySelector<HTMLInputElement>('.skill-years input')!;
    input.value = '5';

    input.dispatchEvent(new Event('change'));

    expect(form.value.skillDetails).toEqual({ Java: { years: 5 } });
  });
});

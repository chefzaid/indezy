import { SkillNamesService } from '../../../services/skill/skill-names.service';
import { SkillNames } from '../../../shared/utils/skill-names';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { UserProfile } from '../../../models/user-management.models';
import { ProfilePersonalInfoComponent } from './profile-personal-info.component';

describe('ProfilePersonalInfoComponent', () => {
  let fixture: ComponentFixture<ProfilePersonalInfoComponent>;
  let component: ProfilePersonalInfoComponent;
  let form: FormGroup;

  const profile: UserProfile = { id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' };

  function create(overrides: Partial<UserProfile> = {}, values: Record<string, unknown> = {}): void {
    TestBed.configureTestingModule({
      imports: [ProfilePersonalInfoComponent, TranslateModule.forRoot()],
      providers: [provideNativeDateAdapter(), { provide: SkillNamesService, useValue: { names$: of(SkillNames.NONE) } }]
    });
    form = new FormBuilder().group({
      firstName: ['Ada', [Validators.required]],
      lastName: ['Lovelace', [Validators.required]],
      email: ['ada@example.com', [Validators.required, Validators.email]],
      phone: [''],
      birthDate: [null],
      address: [''],
      city: [''],
      bio: [''],
      company: [''],
      position: [''],
      targetDailyRate: [null],
      availabilityDate: [null],
      website: [''],
      linkedin: [''],
      github: [''],
      skills: [[]],
      skillDetails: [{}],
      languages: [[]],
      timezone: ['Europe/Paris'],
      currency: ['EUR']
    });
    form.patchValue(values);
    fixture = TestBed.createComponent(ProfilePersonalInfoComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('userProfile', { ...profile, ...overrides });
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(): string {
    return element().textContent!.replace(/\s+/g, ' ');
  }

  function submitButton(): HTMLButtonElement {
    return element().querySelector('button[type="submit"]')!;
  }

  it('shows the user name, the job title and the default avatar', () => {
    create();

    expect(element().querySelector('.avatar-info h3')!.textContent!.trim()).toBe('Ada Lovelace');
    expect(element().querySelector('.avatar-info p')!.textContent!.trim()).toBe('Freelance');
    const avatar = element().querySelector<HTMLImageElement>('img.user-avatar')!;
    expect(avatar.getAttribute('src')).toBe('assets/images/default-avatar.svg');
    expect(avatar.alt).toBe('Ada Lovelace');
  });

  it('shows the stored avatar and position when the profile has them', () => {
    create({ avatar: 'data:image/jpeg;base64,AAAA', position: 'Tech lead' });

    expect(element().querySelector('img.user-avatar')!.getAttribute('src')).toBe('data:image/jpeg;base64,AAAA');
    expect(element().querySelector('.avatar-info p')!.textContent!.trim()).toBe('Tech lead');
  });

  it('falls back to the default avatar once when the image cannot be loaded', () => {
    create({ avatar: 'https://example.com/missing.png' });
    const image = document.createElement('img');
    image.src = 'https://example.com/missing.png';

    component.onAvatarError({ target: image } as unknown as Event);
    expect(image.src).toContain('assets/images/default-avatar.svg');

    const setter = spyOnProperty(image, 'src', 'set').and.callThrough();
    component.onAvatarError({ target: image } as unknown as Event);
    expect(setter).not.toHaveBeenCalled();
  });

  it('adds a language once and removes it', () => {
    create({}, { languages: ['Français'] });

    component.addLanguage('Anglais');
    component.addLanguage('Anglais');
    expect(form.value.languages).toEqual(['Français', 'Anglais']);

    component.removeLanguage('Français');
    component.removeLanguage('Japonais');
    expect(form.value.languages).toEqual(['Anglais']);
  });

  it('treats missing languages as an empty list', () => {
    create({}, { languages: null });

    component.removeLanguage('Anglais');
    expect(form.value.languages).toBeNull();

    component.addLanguage('Anglais');
    expect(form.value.languages).toEqual(['Anglais']);
  });

  it('renders the chosen languages as removable chips and the skills in their editor', () => {
    create({}, { skills: ['Angular', 'Docker'], languages: ['Anglais'] });

    const chips = Array.from(element().querySelectorAll('mat-chip')).map(chip => chip.textContent!.replace('cancel', '').trim());
    expect(chips).toEqual(['Anglais']);
    const skills = Array.from(element().querySelectorAll('.skills-section .skill-name')).map(name => name.textContent!.trim());
    expect(skills).toEqual(['Angular', 'Docker']);

    element().querySelector<HTMLButtonElement>('.languages-section mat-chip button')!.click();
    expect(form.value.languages).toEqual([]);
  });

  it('counts the characters of the bio', () => {
    create({}, { bio: 'Hello' });

    expect(text()).toContain('5/500');
    form.patchValue({ bio: null });
    expect(text()).toContain('0/500');
  });

  it('emits save when the form is submitted', () => {
    create();
    const save = jasmine.createSpy('save');
    component.save.subscribe(save);

    submitButton().click();

    expect(save).toHaveBeenCalledTimes(1);
  });

  it('forwards the selected avatar file', () => {
    create();
    const upload = jasmine.createSpy('avatarUpload');
    component.avatarUpload.subscribe(upload);
    const input = element().querySelector<HTMLInputElement>('input[type="file"]')!;
    const click = spyOn(input, 'click');

    element().querySelector<HTMLButtonElement>('.avatar-edit-btn')!.click();
    expect(click).toHaveBeenCalled();

    input.dispatchEvent(new Event('change'));
    expect(upload).toHaveBeenCalledWith(jasmine.any(Event));
  });

  it('disables saving while the form is invalid and explains the missing fields', () => {
    create({}, { firstName: '', lastName: '', email: '' });
    form.markAllAsTouched();

    expect(submitButton().disabled).toBeTrue();
    expect(text()).toContain('profile.errors.firstNameRequired');
    expect(text()).toContain('profile.errors.lastNameRequired');
    expect(text()).toContain('profile.errors.emailRequired');

    form.patchValue({ email: 'not-an-email' });
    expect(text()).toContain('errors.invalidEmail');
  });

  it('shows the saving state while an update is in flight', () => {
    create();
    expect(submitButton().textContent).toContain('common.save');

    fixture.componentRef.setInput('isUpdating', true);

    expect(submitButton().disabled).toBeTrue();
    expect(submitButton().textContent).toContain('common.saving');
    expect(submitButton().querySelector('mat-spinner')).not.toBeNull();
  });
});

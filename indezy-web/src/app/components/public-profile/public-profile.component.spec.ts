import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { PublicProfileComponent } from './public-profile.component';
import { PublicProfileService } from '../../services/public-profile/public-profile.service';
import { PublicProfile } from '../../models';

describe('PublicProfileComponent', () => {
  let fixture: ComponentFixture<PublicProfileComponent>;
  let profiles: jasmine.SpyObj<PublicProfileService>;

  const profile = (over: Partial<PublicProfile> = {}): PublicProfile => ({
    firstName: 'Alice', lastName: 'Martin', position: 'Java lead', city: 'Paris', bio: 'Java since 2012',
    skills: [{ name: 'Java', level: 'EXPERT', years: 12 }, { name: 'Kafka' }], languages: ['French', 'English'],
    website: 'https://alice.example', linkedin: 'https://linkedin.com/in/alice', availableNow: true, ...over
  });
  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const create = (): void => {
    TestBed.configureTestingModule({
      imports: [PublicProfileComponent, TranslateModule.forRoot()],
      providers: [
        { provide: PublicProfileService, useValue: profiles },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ slug: 'alice' }) } } }
      ]
    });
    fixture = TestBed.createComponent(PublicProfileComponent);
    fixture.detectChanges();
  };

  beforeEach(() => {
    profiles = jasmine.createSpyObj<PublicProfileService>('PublicProfileService', ['view']);
  });

  it('shows the profile, its skills, links and that it is available now', () => {
    profiles.view.and.returnValue(of(profile()));
    create();

    expect(profiles.view).toHaveBeenCalledWith('alice');
    expect(TestBed.inject(Title).getTitle()).toBe('Alice Martin – Indezy');
    expect(text()).toContain('Alice Martin');
    expect(text()).toContain('Java lead');
    expect(text()).toContain('publicProfile.availableNow');
    expect(text()).toContain('profile.skillLevels.EXPERT');
    expect(fixture.componentInstance.initials).toBe('AM');
    const links = (fixture.nativeElement as HTMLElement).querySelectorAll('.links a');
    expect(links.length).toBe(2);
    expect(links[0].getAttribute('rel')).toBe('noopener nofollow');
    expect((fixture.nativeElement as HTMLElement).querySelector('.avatar.initials')).not.toBeNull();
  });

  it('says when the freelancer is available, or that it is to ask, with the photo when there is one', () => {
    profiles.view.and.returnValue(of(profile({ availableNow: false, availableFrom: '2026-11-02', avatar: 'data:image/png;base64,eA==',
      bio: undefined, skills: [], languages: [], website: undefined, linkedin: undefined })));
    create();
    expect(text()).toContain('publicProfile.availableFrom');
    expect((fixture.nativeElement as HTMLElement).querySelector('img.avatar')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.links')).toBeNull();

    fixture.componentInstance.profile = profile({ availableNow: false, availableFrom: undefined });
    fixture.detectChanges();
    expect(text()).toContain('publicProfile.availabilityUnknown');
  });

  it('says there is no profile at an unknown or hidden address', () => {
    profiles.view.and.returnValue(throwError(() => new Error('404')));
    create();
    expect(fixture.componentInstance.notFound).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-profile-not-found]')).not.toBeNull();
    expect(fixture.componentInstance.fullName).toBe('');
  });
});

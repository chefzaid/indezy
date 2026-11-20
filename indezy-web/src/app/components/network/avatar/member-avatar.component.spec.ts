import { TranslateModule } from '@ngx-translate/core';
import { TestBed } from '@angular/core/testing';

import { MemberAvatarComponent, openToWork } from './member-avatar.component';

describe('MemberAvatarComponent', () => {
  function render(avatar: string | null, size?: number): HTMLElement {
    const fixture = TestBed.createComponent(MemberAvatarComponent);
    fixture.componentRef.setInput('subject', { handle: 'leo_m', firstName: 'léo', lastName: 'Martin', avatar });
    if (size) {
      fixture.componentRef.setInput('size', size);
    }
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('shows the initials on a color the handle keeps, or the photo', () => {
    const initials = render(null, 56).querySelector('.initials') as HTMLElement;
    expect(initials.textContent?.trim()).toBe('LM');
    expect(initials.style.width).toBe('56px');
    expect(initials.style.background).toContain('linear-gradient');
    expect(MemberAvatarComponent.hash('leo_m')).toBe(MemberAvatarComponent.hash('leo_m'));
    const photo = render('https://example.test/leo.jpg').querySelector('img') as HTMLImageElement;
    expect(photo.getAttribute('src')).toBe('https://example.test/leo.jpg');
    expect(photo.getAttribute('alt')).toBe('');
  });

  it('wears the Open to work badge for a member available now, on an avatar large enough to read it', () => {
    TestBed.configureTestingModule({ imports: [MemberAvatarComponent, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(MemberAvatarComponent);
    fixture.componentRef.setInput('subject', { handle: 'leo', firstName: 'Léo', lastName: 'Martin', avatar: null, availability: 'AVAILABLE',
      availabilityDate: null });
    fixture.componentRef.setInput('size', 44);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-open-to-work]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.frame.open')).not.toBeNull();
    fixture.componentRef.setInput('size', 24);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-open-to-work]')).toBeNull();
    fixture.componentRef.setInput('subject', { handle: 'leo', firstName: 'Léo', lastName: 'Martin', avatar: null, availability: 'AVAILABLE',
      availabilityDate: '2099-01-04' });
    fixture.componentRef.setInput('size', 44);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.frame.open')).toBeNull();
    expect(openToWork({ handle: 'x', firstName: 'A', lastName: 'B', avatar: null, availability: 'LISTENING' })).toBeFalse();
  });
});

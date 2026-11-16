import { TestBed } from '@angular/core/testing';

import { MemberAvatarComponent } from './member-avatar.component';

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
});

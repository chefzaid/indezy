import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Who an avatar shows: the photo when the member shows one, else their initials. */
export interface AvatarSubject {
  handle: string;
  firstName: string;
  lastName: string;
  avatar: string | null;
}

/** Pairs of colors an avatar's initials sit on, each dark enough for white text (4.5:1 at least). */
const GRADIENTS = [
  ['#4f46e5', '#7c3aed'], ['#0f766e', '#0e7490'], ['#b45309', '#c2410c'], ['#be123c', '#9d174d'],
  ['#1d4ed8', '#4338ca'], ['#15803d', '#047857'], ['#7e22ce', '#a21caf'], ['#334155', '#475569']
];

/** The gradient a member's handle always picks, for their avatar and their page's cover. */
export function memberGradient(handle: string, angle = 135): string {
  const [from, to] = GRADIENTS[MemberAvatarComponent.hash(handle) % GRADIENTS.length];
  return `linear-gradient(${angle}deg, ${from}, ${to})`;
}

/** A member's round avatar: their photo, or their initials on a color their handle always picks. */
@Component({
  selector: 'app-member-avatar',
  template: `@if (subject.avatar) {
    <img class="avatar" [src]="subject.avatar" alt="" [style.width.px]="size" [style.height.px]="size">
  } @else {
    <span class="avatar initials" aria-hidden="true" [style.width.px]="size" [style.height.px]="size"
      [style.font-size.px]="size * 0.4" [style.background]="background">{{ initials }}</span>
  }`,
  styles: [`:host { display: inline-flex; flex: none; }
    .avatar { border-radius: 50%; object-fit: cover; }
    .initials { display: inline-flex; align-items: center; justify-content: center; color: #fff; font-weight: 600;
      letter-spacing: 0.02em; user-select: none; }`],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MemberAvatarComponent {
  @Input({ required: true }) subject!: AvatarSubject;
  /** The diameter, in pixels. */
  @Input() size = 40;

  get initials(): string {
    return `${this.subject.firstName.charAt(0)}${this.subject.lastName.charAt(0)}`.toUpperCase() || '?';
  }

  get background(): string {
    return memberGradient(this.subject.handle);
  }

  /** A small, stable number from the handle, so a member keeps their color. */
  static hash(text: string): number {
    let hash = 0;
    for (let i = 0; i < text.length; i++) {
      hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
    }
    return hash;
  }
}

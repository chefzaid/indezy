import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

/** Who an avatar shows: the photo when the member shows one, else their initials. */
export interface AvatarSubject {
  handle: string;
  firstName: string;
  lastName: string;
  avatar: string | null;
  /** The availability the card tells, when it does: **Open to work** when available from no later day. */
  availability?: string | null;
  availabilityDate?: string | null;
}

/** Whether a member's card says they are available now: their avatar wears the **Open to work** badge. */
export function openToWork(subject: AvatarSubject): boolean {
  return subject.availability === 'AVAILABLE' && !subject.availabilityDate;
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
  template: `<span class="frame" [class.open]="open" [style.width.px]="size" [style.height.px]="size">@if (subject.avatar) {
    <img class="avatar" [src]="subject.avatar" alt="" [style.width.px]="size" [style.height.px]="size">
  } @else {
    <span class="avatar initials" aria-hidden="true" [style.width.px]="size" [style.height.px]="size"
      [style.font-size.px]="size * 0.4" [style.background]="background">{{ initials }}</span>
  }@if (open && size >= 36) {<span class="badge" aria-hidden="true" data-open-to-work [style.font-size.px]="size >= 80 ? 12 : 9">{{ 'network.openToWork.badge' | translate }}</span>}</span>`,
  imports: [TranslatePipe],
  styles: [`:host { display: inline-flex; flex: none; }
    .frame { position: relative; display: inline-flex; border-radius: 50%; }
    .frame.open { box-shadow: 0 0 0 2px #15803d; }
    .badge { position: absolute; bottom: -4px; left: 50%; transform: translateX(-50%); padding: 0 5px; border-radius: 999px;
      background: #15803d; color: #fff; font-weight: 700; line-height: 1.5; letter-spacing: 0.02em; white-space: nowrap;
      box-shadow: 0 0 0 2px var(--app-surface, #fff); }
    .avatar { border-radius: 50%; object-fit: cover; }
    .initials { display: inline-flex; align-items: center; justify-content: center; color: #fff; font-weight: 600;
      letter-spacing: 0.02em; user-select: none; }`],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MemberAvatarComponent {
  @Input({ required: true }) subject!: AvatarSubject;
  /** The diameter, in pixels. */
  @Input() size = 40;

  /** Whether the member is **Open to work**: a green frame, and the badge on an avatar large enough to read it. */
  get open(): boolean {
    return openToWork(this.subject);
  }

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

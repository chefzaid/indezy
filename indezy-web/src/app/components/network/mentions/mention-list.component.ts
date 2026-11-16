import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard } from '../../../models';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/** A choice of the autocompletion: a member to mention, or a tag. */
export interface MentionChoice {
  sigil: '@' | '#';
  /** What the text gets after the sigil: the handle or the tag. */
  value: string;
  member: MemberCard | null;
}

let lists = 0;

/** The members or tags offered while typing after an @ or a #, shown under the text by `appMentions`. */
@Component({
  selector: 'app-mention-list',
  imports: [MemberAvatarComponent, TranslateModule],
  template: `
    <ul class="mention-list" role="listbox" [id]="id" [attr.aria-label]="'network.mentions.label' | translate" data-mention-list>
      @for (choice of choices; track choice.value; let index = $index) {
        <li role="option" [id]="id + '-' + index" [attr.aria-selected]="index === active" [class.active]="index === active"
          (mousedown)="$event.preventDefault()" (click)="picked.emit(choice)" [attr.data-mention-choice]="choice.value">
          @if (choice.member; as member) {
            <app-member-avatar [subject]="member" [size]="28"></app-member-avatar>
            <span class="text"><span class="name">{{ member.firstName }} {{ member.lastName }}</span>
              <span class="sub">&#64;{{ member.handle }}</span></span>
          } @else {
            <span class="sigil" aria-hidden="true">#</span><span class="text"><span class="name">{{ choice.value }}</span></span>
          }
        </li>
      }
    </ul>
  `,
  styles: [`
    .mention-list {
      min-width: 220px;
      max-width: 320px;
      margin: 4px 0 0;
      padding: 4px;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      background: var(--app-surface);
      box-shadow: 0 8px 24px rgba(15, 12, 40, 0.16);
      list-style: none;
    }

    li {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 6px 8px;
      border-radius: 8px;
      cursor: pointer;

      &.active,
      &:hover {
        background: var(--app-selected-bg);
      }
    }

    .text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .name {
      overflow: hidden;
      color: var(--app-text);
      font-weight: 600;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .sub {
      color: var(--app-text-muted);
      font-size: 0.75rem;
    }

    .sigil {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--app-chip-indigo-bg);
      color: var(--app-chip-indigo-text);
      font-weight: 700;
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MentionListComponent {
  private readonly changes = inject(ChangeDetectorRef);

  readonly id = `mention-list-${++lists}`;
  choices: MentionChoice[] = [];
  active = 0;
  readonly picked = new EventEmitter<MentionChoice>();

  show(choices: MentionChoice[], active: number): void {
    this.choices = choices;
    this.active = active;
    this.changes.detectChanges();
  }
}

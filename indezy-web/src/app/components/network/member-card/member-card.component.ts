import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard } from '../../../models';

/**
 * A member as members see them: photo, name and handle, headline, city or remote, availability (also a coloured ring
 * around the photo), stack and languages.
 */
@Component({
  selector: 'app-member-card',
  imports: [DatePipe, MatIconModule, TranslateModule],
  templateUrl: './member-card.component.html',
  styleUrls: ['./member-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberCardComponent {
  @Input({ required: true }) card!: MemberCard;
  /** Without its photo, name and handle, nor its frame: the details under a member page's own header. */
  @Input() bare = false;

  readonly icons = { AVAILABLE: 'event_available', ON_MISSION: 'work', LISTENING: 'hearing' };

  get initials(): string {
    return `${this.card.firstName.charAt(0)}${this.card.lastName.charAt(0)}`.toUpperCase();
  }
}

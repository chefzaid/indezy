import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard } from '../../../models';

/** A member as members see them: photo, name and handle, headline, city or remote, availability, stack and languages. */
@Component({
  selector: 'app-member-card',
  imports: [DatePipe, MatIconModule, TranslateModule],
  templateUrl: './member-card.component.html',
  styleUrls: ['./member-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MemberCardComponent {
  @Input({ required: true }) card!: MemberCard;

  get initials(): string {
    return `${this.card.firstName.charAt(0)}${this.card.lastName.charAt(0)}`.toUpperCase();
  }
}

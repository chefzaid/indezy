import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { RefusedBy } from '../../../models/project.models';

/** Who refused an opportunity, as a badge: red when the recruiter did, orange when the freelance did. */
@Component({
  selector: 'app-refused-by-badge',
  imports: [TranslateModule],
  template: `
    <span class="refused-by" [class.by-recruiter]="refusedBy === 'RECRUITER'" [attr.data-refused-by]="refusedBy">
      {{ 'projects.refusedBy.badge.' + refusedBy | translate }}
    </span>
  `,
  styles: [`
    :host { display: inline-flex; }
    .refused-by { padding: 2px 8px; border-radius: 10px; font-size: 0.75rem; font-weight: 500; line-height: 1.4;
      background: var(--app-refused-by-freelance); color: var(--app-refused-by-freelance-text); }
    .refused-by.by-recruiter { background: var(--app-refused-by-recruiter); color: var(--app-refused-by-recruiter-text); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RefusedByBadgeComponent {
  @Input({ required: true }) refusedBy!: RefusedBy;
}

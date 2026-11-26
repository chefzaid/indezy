import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { AuthService } from '../../../services/auth/auth.service';

/**
 * Shared footer displayed across every Indezy page (presentation website, public pages and authenticated workspace).
 * Keeps the copyright, tagline and legal/help/contact links in one place; `contained` aligns its inner width to the
 * presentation page (`1120px`), otherwise it spans the page width.
 */
@Component({
  selector: 'app-site-footer',
  imports: [RouterLink, TranslateModule],
  templateUrl: './site-footer.component.html',
  styleUrls: ['./site-footer.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SiteFooterComponent {
  readonly auth = inject(AuthService);
  readonly year = new Date().getFullYear();
  /** When true (presentation website), constrains the inner width to the website container. */
  @Input() contained = false;
}

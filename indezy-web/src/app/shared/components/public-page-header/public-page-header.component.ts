import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

import { AppLanguage, LANGUAGE_STORAGE_KEY, getSavedLanguage } from '../../locale/app-locale';
import { LogoComponent } from '../logo/logo.component';

/** Header of the pages opened without an account: the logo, and French or English for whoever reads them. */
@Component({
  selector: 'app-public-page-header',
  imports: [MatButtonModule, TranslateModule, LogoComponent],
  templateUrl: './public-page-header.component.html',
  styleUrls: ['./public-page-header.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PublicPageHeaderComponent {
  readonly language: AppLanguage = getSavedLanguage();

  switchLanguage(language: AppLanguage): void {
    if (language === this.language) {
      return;
    }
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    this.reloadPage();
  }

  /** Separated so tests can stub the full page reload, which keeps the address and any token in it. */
  reloadPage(): void {
    window.location.reload();
  }
}

import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth/auth.service';

/**
 * Shows the help in the language of its address (/aide in French, /en/help in English) to a visitor without an account;
 * a member keeps the language of their preferences.
 */
export function useAddressLanguage(route: ActivatedRoute, auth: AuthService, translate: TranslateService): void {
  const language = route.snapshot?.data?.['language'];
  if ((language === 'fr' || language === 'en') && !auth.isAuthenticated()) {
    translate.use(language);
    globalThis.document?.documentElement?.setAttribute('lang', language);
  }
}

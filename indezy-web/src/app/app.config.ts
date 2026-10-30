import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { errorInterceptor } from './interceptors/error.interceptor';
import { AuthService } from './services/auth/auth.service';
import { provideAppLocale } from './shared/locale/app-locale';
import { ThemeService } from './shared/theme/theme.service';
import { TranslatedPaginatorIntl } from './shared/i18n/translated-paginator-intl';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    // Angular 21+ bootstraps zoneless by default. The components update plain
    // fields from HTTP subscriptions and rely on Zone.js to refresh the view,
    // so keep zone-based change detection explicitly enabled.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withViewTransitions()
    ),
    provideHttpClient(
      withInterceptors([authInterceptor, errorInterceptor])
    ),
    // Production only: the app and the workspace's last answers stay readable offline (ngsw-config.json).
    provideServiceWorker('ngsw-worker.js', { enabled: environment.production, registrationStrategy: 'registerWhenStable:30000' }),
    provideAppLocale(),
    { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
    provideAppInitializer(() => inject(ThemeService).restore()),
    provideAppInitializer(() => inject(AuthService).initializeSso()),
    provideTranslateService({
      fallbackLang: 'fr',
      loader: provideTranslateHttpLoader({ prefix: './assets/i18n/', suffix: '.json' })
    })
  ]
};

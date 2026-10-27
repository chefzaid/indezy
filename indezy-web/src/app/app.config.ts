import { ApplicationConfig, importProvidersFrom, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader, provideTranslateHttpLoader } from '@ngx-translate/http-loader';

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
    provideAnimationsAsync(),
    // Production only: the app and the workspace's last answers stay readable offline (ngsw-config.json).
    provideServiceWorker('ngsw-worker.js', { enabled: environment.production, registrationStrategy: 'registerWhenStable:30000' }),
    provideAppLocale(),
    { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
    provideAppInitializer(() => inject(ThemeService).restore()),
    provideAppInitializer(() => inject(AuthService).initializeSso()),
    importProvidersFrom(
      MatSnackBarModule,
      MatDialogModule,
      TranslateModule.forRoot({
        fallbackLang: 'fr',
        loader: {
          provide: TranslateLoader,
          useClass: TranslateHttpLoader
        }
      })
    ),
    ...provideTranslateHttpLoader({ prefix: './assets/i18n/', suffix: '.json' })
  ]
};

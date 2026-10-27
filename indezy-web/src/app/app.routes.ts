import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { projectLinkGuard } from './guards/project-link.guard';
import { onboardingGuard } from './guards/onboarding.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./components/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./components/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    // Opened by clients and ESNs from an activity report's validation link, without an account.
    path: 'activity-report',
    loadComponent: () => import('./components/activity-report-validation/activity-report-validation.component')
      .then(m => m.ActivityReportValidationComponent)
  },
  {
    // Opened from a saved search email's unsubscribe link, without an account.
    path: 'emails/unsubscribe',
    loadComponent: () => import('./components/emails-unsubscribe/emails-unsubscribe.component').then(m => m.EmailsUnsubscribeComponent)
  },
  {
    // A freelancer's public profile, opened from its link without an account.
    path: 'p/:slug',
    loadComponent: () => import('./components/public-profile/public-profile.component').then(m => m.PublicProfileComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard, onboardingGuard, projectLinkGuard],
    runGuardsAndResolvers: 'paramsOrQueryParamsChange'
  },
  {
    path: 'offers',
    loadComponent: () => import('./components/offers/offer-feed/offer-feed.component').then(m => m.OfferFeedComponent),
    canActivate: [authGuard]
  },
  {
    path: 'offers/sites',
    loadComponent: () => import('./components/offers/offer-sites/offer-sites.component').then(m => m.OfferSitesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'offers/publishers/:name',
    loadComponent: () => import('./components/offers/offer-publisher/offer-publisher.component').then(m => m.OfferPublisherComponent),
    canActivate: [authGuard]
  },
  {
    path: 'offers/:id',
    loadComponent: () => import('./components/offers/offer-page/offer-page.component').then(m => m.OfferPageComponent),
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    loadComponent: () => import('./components/profile/profile.component').then(m => m.ProfileComponent),
    canActivate: [authGuard]
  },
  {
    path: 'timeline',
    loadComponent: () => import('./components/timeline/timeline.component').then(m => m.TimelineComponent),
    canActivate: [authGuard]
  },
  {
    path: 'calendar',
    loadComponent: () => import('./components/calendar/calendar.component').then(m => m.CalendarComponent),
    canActivate: [authGuard]
  },
  {
    path: 'welcome',
    loadComponent: () => import('./components/onboarding/onboarding.component').then(m => m.OnboardingComponent),
    canActivate: [authGuard]
  },
  {
    path: 'review',
    loadComponent: () => import('./components/review/yearly-review.component').then(m => m.YearlyReviewComponent),
    canActivate: [authGuard]
  },
  {
    path: 'archive',
    loadComponent: () => import('./components/archive/archive.component').then(m => m.ArchiveComponent),
    canActivate: [authGuard]
  },
  {
    path: 'projects',
    loadChildren: () => import('./components/projects/projects.routes').then(m => m.projectRoutes),
    canActivate: [authGuard]
  },
  {
    path: 'clients',
    loadChildren: () => import('./components/clients/clients.routes').then(m => m.clientRoutes),
    canActivate: [authGuard]
  },
  {
    path: 'contacts',
    loadChildren: () => import('./components/contacts/contacts.routes').then(m => m.contactRoutes),
    canActivate: [authGuard]
  },
  {
    path: 'sources',
    loadChildren: () => import('./components/sources/sources.routes').then(m => m.sourceRoutes),
    canActivate: [authGuard]
  },
  {
    path: '404',
    loadComponent: () => import('./components/error/not-found/not-found.component').then(m => m.NotFoundComponent)
  },
  {
    path: 'error',
    loadComponent: () => import('./components/error/server-error/server-error.component').then(m => m.ServerErrorComponent)
  },
  {
    path: '**',
    loadComponent: () => import('./components/error/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];

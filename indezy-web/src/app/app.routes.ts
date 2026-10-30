import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { projectLinkGuard } from './guards/project-link.guard';
import { onboardingGuard } from './guards/onboarding.guard';
import { adminGuard, featureGuard } from './guards/capability.guards';

export const routes: Routes = [
  {
    // The presentation page, in French at the root and English under /en; an old shared ?project= link opens its mission.
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent),
    canActivate: [projectLinkGuard],
    data: { language: 'fr' }
  },
  {
    path: 'en',
    pathMatch: 'full',
    loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent),
    data: { language: 'en' }
  },
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
    // Documents a freelancer shares with an ESN, opened from the link without an account.
    path: 'documents-pack',
    loadComponent: () => import('./components/compliance-pack/compliance-pack.component').then(m => m.CompliancePackComponent)
  },
  {
    // A freelancer's booking link, opened by recruiters without an account.
    path: 'book/:slug',
    loadComponent: () => import('./components/book/book.component').then(m => m.BookComponent)
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
    canActivate: [authGuard, featureGuard('OFFER_FEED')]
  },
  {
    path: 'offers/sites',
    loadComponent: () => import('./components/offers/offer-sites/offer-sites.component').then(m => m.OfferSitesComponent),
    canActivate: [authGuard, featureGuard('OFFER_FEED')]
  },
  {
    path: 'offers/publishers/:name',
    loadComponent: () => import('./components/offers/offer-publisher/offer-publisher.component').then(m => m.OfferPublisherComponent),
    canActivate: [authGuard, featureGuard('OFFER_FEED')]
  },
  {
    path: 'offers/:id',
    loadComponent: () => import('./components/offers/offer-page/offer-page.component').then(m => m.OfferPageComponent),
    canActivate: [authGuard, featureGuard('OFFER_FEED')]
  },
  {
    path: 'profile',
    loadComponent: () => import('./components/profile/profile.component').then(m => m.ProfileComponent),
    canActivate: [authGuard]
  },
  {
    // The former timeline page is now the calendar's upcoming view.
    path: 'timeline',
    redirectTo: '/calendar?view=upcoming'
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
    path: 'admin',
    loadComponent: () => import('./components/admin/admin.component').then(m => m.AdminComponent),
    canActivate: [authGuard, adminGuard]
  },
  {
    path: 'review',
    loadComponent: () => import('./components/review/yearly-review.component').then(m => m.YearlyReviewComponent),
    canActivate: [authGuard, featureGuard('YEARLY_REVIEW')]
  },
  {
    path: 'documents',
    loadComponent: () => import('./components/compliance/compliance.component').then(m => m.ComplianceComponent),
    canActivate: [authGuard]
  },
  {
    // The missions in progress, with their activity reports to fill in.
    path: 'activity-reports',
    loadComponent: () => import('./components/activity-reports/activity-reports.component').then(m => m.ActivityReportsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'invoices',
    loadComponent: () => import('./components/invoices/invoices.component').then(m => m.InvoicesComponent),
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
    path: '**',
    loadComponent: () => import('./components/error/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];

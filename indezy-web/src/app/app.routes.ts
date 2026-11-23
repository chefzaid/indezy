import { Routes } from '@angular/router';
import { ownNetworkTab } from './guards/own-network-tab.guard';
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
    // Opened by a client contact from a testimonial link, without an account.
    path: 'testimonial',
    loadComponent: () => import('./components/testimonial-writing/testimonial-writing.component').then(m => m.TestimonialWritingComponent)
  },
  {
    // Opened by clients and ESNs from an activity report's validation link, without an account.
    path: 'activity-report',
    loadComponent: () => import('./components/activity-report-validation/activity-report-validation.component')
      .then(m => m.ActivityReportValidationComponent)
  },
  {
    // An invitation to the network, opened from its email without an account.
    path: 'network-invitation',
    loadComponent: () => import('./components/network-invitation/network-invitation.component').then(m => m.NetworkInvitationComponent)
  },
  {
    // The help center and its articles, open with or without an account.
    path: 'help',
    loadComponent: () => import('./components/help/help-center.component').then(m => m.HelpCenterComponent)
  },
  {
    path: 'help/:slug',
    loadComponent: () => import('./components/help/help-article.component').then(m => m.HelpArticleComponent)
  },
  {
    // The help center's addresses in each language, as search engines find them (scripts/prerender-help.mjs).
    path: 'aide',
    loadComponent: () => import('./components/help/help-center.component').then(m => m.HelpCenterComponent),
    data: { language: 'fr' }
  },
  {
    path: 'aide/:slug',
    loadComponent: () => import('./components/help/help-article.component').then(m => m.HelpArticleComponent),
    data: { language: 'fr' }
  },
  {
    path: 'en/help',
    loadComponent: () => import('./components/help/help-center.component').then(m => m.HelpCenterComponent),
    data: { language: 'en' }
  },
  {
    path: 'en/help/:slug',
    loadComponent: () => import('./components/help/help-article.component').then(m => m.HelpArticleComponent),
    data: { language: 'en' }
  },
  {
    // The quarterly market report of the feed's rates, open without an account.
    path: 'market-report',
    loadComponent: () => import('./components/market-report/market-report.component').then(m => m.MarketReportComponent)
  },
  {
    // The legal notice, terms of use and privacy policy, open without an account.
    path: 'legal/:document',
    loadComponent: () => import('./components/legal/legal-page.component').then(m => m.LegalPageComponent)
  },
  {
    path: 'legal',
    pathMatch: 'full',
    redirectTo: 'legal/notice'
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
    path: 'support',
    loadComponent: () => import('./components/support/support.component').then(m => m.SupportComponent),
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
    path: 'expenses',
    loadComponent: () => import('./components/expenses/expenses.component').then(m => m.ExpensesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'network',
    pathMatch: 'full',
    loadComponent: () => import('./components/network/feed/network-feed.component').then(m => m.NetworkFeedComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // The reader's conversations, and the one open beside them.
    path: 'network/messages',
    loadComponent: () => import('./components/network/messages/messages-page.component').then(m => m.MessagesPageComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/messages/:id',
    loadComponent: () => import('./components/network/messages/messages-page.component').then(m => m.MessagesPageComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // A post's own page, its comments open.
    path: 'network/posts/:id',
    loadComponent: () => import('./components/network/post-page/post-page.component').then(m => m.PostPageComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/tags/:tag',
    loadComponent: () => import('./components/network/tag-page/tag-page.component').then(m => m.TagPageComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/notifications',
    loadComponent: () => import('./components/network/notifications/network-notifications.component').then(m => m.NetworkNotificationsComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // The drafts and the saved posts are tabs of the reader's own page; their former addresses lead there.
    path: 'network/drafts',
    children: [],
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK'), ownNetworkTab('drafts')]
  },
  {
    path: 'network/saved',
    children: [],
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK'), ownNetworkTab('saved')]
  },
  {
    // The network's communities, and each community's page.
    path: 'network/communities',
    loadComponent: () => import('./components/network/communities/network-communities.component').then(m => m.NetworkCommunitiesComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/communities/:slug',
    loadComponent: () => import('./components/network/communities/network-community.component').then(m => m.NetworkCommunityComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // The network's events, and each event's page.
    path: 'network/events',
    loadComponent: () => import('./components/network/events/network-events.component').then(m => m.NetworkEventsComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/events/:id',
    loadComponent: () => import('./components/network/events/network-event.component').then(m => m.NetworkEventComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // The ideas board, and one idea shown first.
    path: 'network/ideas',
    loadComponent: () => import('./components/network/ideas/network-ideas.component').then(m => m.NetworkIdeasComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/ideas/:id',
    loadComponent: () => import('./components/network/ideas/network-ideas.component').then(m => m.NetworkIdeasComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // The network's search results: members, posts and tags for what was typed (`?q=`), a tab in `?tab=`.
    path: 'network/search',
    loadComponent: () => import('./components/network/search-results/network-search-results.component').then(m => m.NetworkSearchResultsComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'network/members',
    loadComponent: () => import('./components/network/member-directory/member-directory.component').then(m => m.MemberDirectoryComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    // A member's page in the member network, for members.
    path: 'm/:handle',
    loadComponent: () => import('./components/network/member-page/member-page.component').then(m => m.MemberPageComponent),
    canActivate: [authGuard, featureGuard('SOCIAL_NETWORK')]
  },
  {
    path: 'quotes',
    loadComponent: () => import('./components/quotes/quotes.component').then(m => m.QuotesComponent),
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

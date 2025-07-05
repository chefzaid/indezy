import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./components/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./components/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    loadComponent: () => import('./components/profile/profile.component').then(m => m.ProfileComponent),
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
    path: '**',
    redirectTo: '/login'
  }
];

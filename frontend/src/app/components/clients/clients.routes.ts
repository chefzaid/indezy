import { Routes } from '@angular/router';

export const clientRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./client-list/client-list.component').then(m => m.ClientListComponent)
  },
  {
    path: 'create',
    loadComponent: () => import('./client-form/client-form.component').then(m => m.ClientFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./client-detail/client-detail.component').then(m => m.ClientDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./client-form/client-form.component').then(m => m.ClientFormComponent)
  },
  {
    path: ':id/contacts/create',
    loadComponent: () => import('../contacts/contact-form/contact-form.component').then(m => m.ContactFormComponent)
  },
  {
    path: ':id/contacts/:contactId/edit',
    loadComponent: () => import('../contacts/contact-form/contact-form.component').then(m => m.ContactFormComponent)
  }
];

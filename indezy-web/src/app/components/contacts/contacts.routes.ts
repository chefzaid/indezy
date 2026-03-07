import { Routes } from '@angular/router';

export const contactRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./contact-list/contact-list.component').then(m => m.ContactListComponent)
  },
  {
    path: 'create',
    loadComponent: () => import('./contact-form/contact-form.component').then(m => m.ContactFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./contact-detail/contact-detail.component').then(m => m.ContactDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./contact-form/contact-form.component').then(m => m.ContactFormComponent)
  }
];

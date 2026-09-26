import { Routes } from '@angular/router';

export const contactRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./contact-list/contact-list.component').then(m => m.ContactListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./contact-form/contact-form.component').then(m => m.ContactFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./contact-detail/contact-detail.component').then(m => m.ContactDetailComponent)
  },
  {
    // Named contactId (not id) so the form never mistakes it for the client id used by
    // the nested /clients/:id/contacts/... routes.
    path: ':contactId/edit',
    loadComponent: () => import('./contact-form/contact-form.component').then(m => m.ContactFormComponent)
  }
];

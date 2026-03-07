import { Routes } from '@angular/router';

export const sourceRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./source-list/source-list.component').then(m => m.SourceListComponent)
  },
  {
    path: 'create',
    loadComponent: () => import('./source-form/source-form.component').then(m => m.SourceFormComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./source-form/source-form.component').then(m => m.SourceFormComponent)
  }
];

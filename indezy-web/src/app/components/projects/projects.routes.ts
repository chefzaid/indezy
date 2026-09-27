import { Routes } from '@angular/router';
import { projectLinkGuard } from '../../guards/project-link.guard';

export const projectRoutes: Routes = [
  {
    path: '',
    canActivate: [projectLinkGuard],
    runGuardsAndResolvers: 'paramsOrQueryParamsChange',
    loadComponent: () => import('./project-list/project-list.component').then(m => m.ProjectListComponent)
  },
  {
    path: 'new',
    loadComponent: () => import('./project-form/project-form.component').then(m => m.ProjectFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./project-detail/project-detail.component').then(m => m.ProjectDetailComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./project-form/project-form.component').then(m => m.ProjectFormComponent)
  }
];

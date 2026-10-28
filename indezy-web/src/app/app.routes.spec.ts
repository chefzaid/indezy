import { Routes } from '@angular/router';
import { routes } from './app.routes';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/capability.guards';
import { projectLinkGuard } from './guards/project-link.guard';

describe('application routes', () => {
  it('opens the presentation page at the root in French and at /en in English, for every visitor', async () => {
    const root = routes.find((route) => route.path === '');
    const english = routes.find((route) => route.path === 'en');

    expect(root?.redirectTo).toBeUndefined();
    expect(root?.pathMatch).toBe('full');
    expect(root?.data).toEqual({ language: 'fr' });
    expect(root?.canActivate).toEqual([projectLinkGuard]);
    expect(english?.data).toEqual({ language: 'en' });
    expect(english?.canActivate).toBeUndefined();
    expect(await root?.loadComponent?.()).toBeDefined();
    expect(await english?.loadComponent?.()).toBeDefined();
  });

  it('opens activity report validation links without an account', () => {
    const validation = routes.find((route) => route.path === 'activity-report');

    expect(validation?.loadComponent).toBeDefined();
    expect(validation?.canActivate).toBeUndefined();
  });

  it('keeps the offer feed for signed-in accounts and loads it lazily', async () => {
    const offers = routes.find((route) => route.path === 'offers');

    expect(offers?.canActivate?.[0]).toBe(authGuard);
    expect(offers?.canActivate?.length).toBe(2);
    const component = await offers?.loadComponent?.();
    expect(component).toBeDefined();
  });

  it('sends the former timeline address to the calendar\'s upcoming view', () => {
    expect(routes.find((route) => route.path === 'timeline')?.redirectTo).toBe('/calendar?view=upcoming');
  });

  it('opens the admin console to signed-in administrators only', () => {
    const admin = routes.find((route) => route.path === 'admin');

    expect(admin?.canActivate).toEqual([authGuard, adminGuard]);
  });
});

describe('lazy loading', () => {
  const childRoutes: Record<string, () => Promise<Routes>> = {
    projects: () => import('./components/projects/projects.routes').then(m => m.projectRoutes),
    clients: () => import('./components/clients/clients.routes').then(m => m.clientRoutes),
    contacts: () => import('./components/contacts/contacts.routes').then(m => m.contactRoutes),
    sources: () => import('./components/sources/sources.routes').then(m => m.sourceRoutes)
  };

  async function expectLazy(list: Routes): Promise<void> {
    for (const route of list.filter((candidate) => !candidate.redirectTo)) {
      expect(route.component).withContext(`${route.path} is bundled eagerly`).toBeUndefined();
      const loaded = await (route.loadComponent ?? route.loadChildren)?.();
      expect(loaded).withContext(`${route.path} loads nothing`).toBeDefined();
    }
  }

  it('loads every page of the application on demand', async () => {
    await expectLazy(routes);
  });

  it('loads every page of the projects, clients, contacts and sources sections on demand', async () => {
    for (const [section, load] of Object.entries(childRoutes)) {
      const loaded = await load();
      expect(loaded.length).withContext(section).toBeGreaterThan(0);
      await expectLazy(loaded);
    }
  });
});

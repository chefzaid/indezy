import { routes } from './app.routes';

describe('application routes', () => {
  it('lands an authenticated root session on the dashboard', () => {
    const root = routes.find((route) => route.path === '');

    expect(root?.redirectTo).toBe('dashboard');
    expect(root?.pathMatch).toBe('full');
  });

  it('opens activity report validation links without an account', () => {
    const validation = routes.find((route) => route.path === 'activity-report');

    expect(validation?.loadComponent).toBeDefined();
    expect(validation?.canActivate).toBeUndefined();
  });
});

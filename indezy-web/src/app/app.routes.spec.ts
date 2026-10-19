import { routes } from './app.routes';
import { authGuard } from './guards/auth.guard';

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

  it('keeps the offer feed for signed-in accounts and loads it lazily', async () => {
    const offers = routes.find((route) => route.path === 'offers');

    expect(offers?.canActivate).toEqual([authGuard]);
    const component = await offers?.loadComponent?.();
    expect(component).toBeDefined();
  });
});

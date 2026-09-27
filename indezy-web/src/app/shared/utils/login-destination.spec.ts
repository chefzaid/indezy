import { loginDestination } from './login-destination';

describe('login destination', () => {
  it('keeps internal paths and query parameters', () => {
    expect(loginDestination('/projects?project=42')).toBe('/projects?project=42');
    expect(loginDestination('/dashboard?project=42')).toBe('/dashboard?project=42');
  });
  it('rejects external, malformed and recursive destinations', () => {
    for (const path of [null, '', 'https://example.test', '//example.test', '/\\example.test', '/\nexample', '/login?returnUrl=/login', '/register']) {
      expect(loginDestination(path)).toBe('/dashboard');
    }
  });
});

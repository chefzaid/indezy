import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { projectLinkGuard } from './project-link.guard';

describe('shared project links', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  function resolve(query: Record<string, string | string[]>): string | boolean {
    const result = TestBed.runInInjectionContext(() => projectLinkGuard(
      { queryParamMap: convertToParamMap(query) } as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    return typeof result === 'boolean' ? result : TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('leaves ordinary routes alone and opens a valid project', () => {
    expect(resolve({})).toBeTrue();
    expect(resolve({ project: '42' })).toBe('/projects/42');
  });

  it('rejects ambiguous, malformed and unsafe identifiers without requesting a project', () => {
    for (const project of ['', '0', '-1', '1.5', '1e3', '0042', '9007199254740992', 'https://example.test', ['1', '2']]) {
      expect(resolve({ project })).toBe('/404');
    }
  });
});

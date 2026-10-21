import { COVERAGE_SOURCES } from './coverage-sources';

/** Loads every source file, so the coverage report counts the files no spec imports, at 0%, instead of leaving them out. */
describe('coverage sources', () => {
  it('loads every source file of the application', async () => {
    for (const source of COVERAGE_SOURCES) {
      expect(await import(`./${source}.ts`)).withContext(source).toBeTruthy();
    }
    expect(COVERAGE_SOURCES).toContain('components/projects/project-list/project-list.component');
  });
});

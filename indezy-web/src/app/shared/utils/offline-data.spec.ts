import { clearOfflineData, OFFLINE_DATA_GROUP } from './offline-data';

describe('clearOfflineData', () => {
  it('deletes the service worker caches of the workspace answers, and only them', async () => {
    const storage = jasmine.createSpyObj<CacheStorage>('CacheStorage', ['keys', 'delete']);
    storage.keys.and.resolveTo([`ngsw:/:1:data:${OFFLINE_DATA_GROUP}:cache`, `ngsw:/:db:1:data:${OFFLINE_DATA_GROUP}:lru`,
      'ngsw:/:1:assets:app:cache']);
    storage.delete.and.resolveTo(true);

    await clearOfflineData(storage);

    expect(storage.delete.calls.allArgs()).toEqual([[`ngsw:/:1:data:${OFFLINE_DATA_GROUP}:cache`],
      [`ngsw:/:db:1:data:${OFFLINE_DATA_GROUP}:lru`]]);
  });

  it('does nothing without cache storage', async () => {
    await expectAsync(clearOfflineData(null)).toBeResolved();
  });
});

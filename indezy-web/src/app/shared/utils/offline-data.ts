/** The service worker's group of cached workspace answers (ngsw-config.json), kept for reading offline. */
export const OFFLINE_DATA_GROUP = 'workspace-reads';

/**
 * Forgets the workspace answers the service worker kept, so the next person on this browser cannot read them offline.
 * Its caches are named after the data group; nothing happens where the browser has no cache storage.
 */
export async function clearOfflineData(storage: CacheStorage | null = globalThis.caches ?? null): Promise<void> {
  if (!storage) {
    return;
  }
  const names = await storage.keys();
  await Promise.all(names.filter(name => name.includes(`:${OFFLINE_DATA_GROUP}:`)).map(name => storage.delete(name)));
}

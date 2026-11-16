import { TestBed } from '@angular/core/testing';

import { AuthService } from '../auth/auth.service';
import { NetworkEvent, NetworkStreamService } from './network-stream.service';

describe('NetworkStreamService', () => {
  let token: string | null;
  let service: NetworkStreamService;

  /** A response whose body sends the chunks, then ends. */
  function streamed(chunks: string[], status = 200): Response {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)));
        controller.close();
      }
    });
    return new Response(status === 200 ? body : null, { status, headers: { 'Content-Type': 'text/event-stream' } });
  }

  const settle = (ms = 20): Promise<void> => new Promise(resolve => setTimeout(resolve, ms));

  beforeEach(() => {
    token = 'jwt';
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { getToken: () => token } }] });
    service = TestBed.inject(NetworkStreamService);
  });

  afterEach(() => service.stop());

  it('reads the events with the token, says it is open, and opens again when the stream ends', async () => {
    const fetches = spyOn(window, 'fetch').and.returnValues(
      Promise.resolve(streamed(['event:ready\ndata:{}\n\n', ':ping\n\nevent:mess', 'age\r\ndata:{"type":"message","conversationId":4}\n\n',
        'event:typing\ndata:{"conversationId":4,"name":"Léo"}\n\nevent:notice\ndata:not json\n\nevent:read\n\n'])),
      new Promise<Response>(() => undefined));
    const events: NetworkEvent[] = [];
    const open: boolean[] = [];
    service.events.subscribe(event => events.push(event));
    service.open.subscribe(state => open.push(state));
    service.start();
    service.start();
    await settle();
    const [url, init] = fetches.calls.first().args as [string, RequestInit];
    expect(url).toMatch(/\/network\/stream$/);
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer jwt');
    expect(events).toEqual([{ type: 'message', conversationId: 4 }, { type: 'typing', conversationId: 4, name: 'Léo' }]);
    expect(open).toEqual([false, true, false]);
    // Ended at once: opened again after a pause, not in a loop.
    expect(fetches).toHaveBeenCalledTimes(1);
    await settle(2_100);
    expect(fetches).toHaveBeenCalledTimes(2);
    expect(service.connected).toBeFalse();
  });

  it('waits longer after each failure, and opens no more once refused or signed out', async () => {
    jasmine.clock().install();
    try {
      const fetches = spyOn(window, 'fetch').and.returnValues(Promise.reject(new Error('offline')), Promise.resolve(streamed([], 500)),
        Promise.resolve(streamed([], 404)));
      service.start();
      await Promise.resolve();
      await Promise.resolve();
      jasmine.clock().tick(2_000);
      for (let i = 0; i < 5; i++) {
        await Promise.resolve();
      }
      expect(fetches).toHaveBeenCalledTimes(2);
      jasmine.clock().tick(3_999);
      expect(fetches).toHaveBeenCalledTimes(2);
      jasmine.clock().tick(1);
      for (let i = 0; i < 5; i++) {
        await Promise.resolve();
      }
      expect(fetches).toHaveBeenCalledTimes(3);
      jasmine.clock().tick(120_000);
      expect(fetches).toHaveBeenCalledTimes(3);
    } finally {
      jasmine.clock().uninstall();
    }
    token = null;
    service.stop();
    service.start();
    expect(service.connected).toBeFalse();
  });

  it('opens again at once after a stream that lasted', async () => {
    const fetches = spyOn(window, 'fetch').and.returnValues(Promise.resolve(streamed(['event:ready\ndata:{}\n\n'])),
      new Promise<Response>(() => undefined));
    // Opened, then ended 15 minutes later.
    spyOn(Date, 'now').and.returnValues(0, 15 * 60 * 1000);
    service.start();
    await settle();
    expect(fetches).toHaveBeenCalledTimes(2);
  });
});

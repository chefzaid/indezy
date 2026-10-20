import { Subject, of, throwError } from 'rxjs';
import { Offer, OfferPage, OfferQuery } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { defaultOfferQuery } from '../../../services/offer/offer-query';
import { OfferBulkActions, OfferPageHost } from './offer-bulk-actions';

describe('OfferBulkActions', () => {
  let offers: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let host: OfferPageHost & { retried: number; redrawn: number };
  let bulk: OfferBulkActions;
  let undo: Subject<void>;

  function offer(id: number, overrides: Partial<Offer> = {}): Offer {
    return { id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://example.test/${id}`, title: `Mission ${id}`, skills: [],
      publishedAt: '2026-10-20T08:00:00Z', datePrecision: 'EXACT', expired: false, saved: false, hidden: false, seen: false, ...overrides };
  }

  function setUp(view: OfferQuery['view'] = 'FEED', content: Offer[] = [offer(1), offer(2), offer(3)]): void {
    offers = jasmine.createSpyObj('OfferService', ['updateStates', 'importOffers']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error', 'info', 'successWithAction']);
    undo = new Subject<void>();
    notifications.successWithAction.and.returnValue(undo.asObservable());
    offers.updateStates.and.returnValue(of([]));
    const page: OfferPage = { content, page: 0, size: 20, totalElements: 30, totalPages: 2, distanceAvailable: false, matchAvailable: false };
    host = { page, query: { ...defaultOfferQuery(), view }, importing: new Set<number>(), retried: 0, redrawn: 0,
      retry() { this.retried++; }, redraw() { this.redrawn++; } };
    bulk = new OfferBulkActions(host, offers, notifications);
  }

  it('selects offers one by one, the whole page, or none', () => {
    setUp();
    bulk.toggle(offer(2));
    bulk.toggle(offer(3));
    bulk.toggle(offer(2));
    expect([...bulk.selected]).toEqual([3]);

    bulk.selectPage();
    expect([...bulk.selected].sort()).toEqual([1, 2, 3]);
    bulk.clear();
    expect(bulk.selected.size).toBe(0);
    expect(host.redrawn).toBe(5);
  });

  it('saves the selected offers at once and puts them back on failure', () => {
    setUp();
    bulk.toggle(offer(1));
    bulk.toggle(offer(3));

    bulk.act({ update: { saved: true } });
    expect(offers.updateStates).toHaveBeenCalledWith([1, 3], { saved: true });
    expect(host.page!.content.map(item => item.saved)).toEqual([true, false, true]);
    expect(bulk.selected.size).toBe(0);
    expect(notifications.success).toHaveBeenCalledWith('offers.bulk.done', 4000, { count: 2 });

    offers.updateStates.and.returnValue(throwError(() => new Error('down')));
    bulk.toggle(offer(2));
    bulk.act({ update: { seen: true } });
    expect(host.page!.content[1].seen).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.state');
  });

  it('takes hidden offers out of the feed and brings them back on undo', () => {
    setUp();
    bulk.toggle(offer(2));

    bulk.act({ update: { hidden: true } });
    expect(host.page!.content.map(item => item.id)).toEqual([1, 3]);
    expect(host.page!.totalElements).toBe(29);
    expect(notifications.successWithAction).toHaveBeenCalledWith('offers.bulk.hiddenOne', 'offers.bulk.undo', 8000, { count: 1 });

    undo.next();
    expect(offers.updateStates).toHaveBeenCalledWith([2], { hidden: false });
    expect(host.retried).toBe(1);
  });

  it('does nothing without a selection', () => {
    setUp();
    bulk.act({ update: { saved: true } });
    expect(offers.updateStates).not.toHaveBeenCalled();
  });

  it('adds the selected offers not yet in the pipeline, fifty per request', () => {
    const content = Array.from({ length: 60 }, (_, index) => offer(index + 1, index === 0 ? { importedProjectId: 9 } : {}));
    setUp('FEED', content);
    offers.importOffers.and.callFake(ids => of({ imported: ids.slice(1).map(id => ({ offerId: id, projectId: 100 + id })),
      alreadyInPipeline: [{ offerId: ids[0], projectId: 7 }], missing: [] }));
    bulk.selectPage();

    bulk.act('add');

    expect(offers.importOffers.calls.allArgs().map(([ids]) => ids.length)).toEqual([50, 9]);
    expect(host.page!.content[2]).toEqual(jasmine.objectContaining({ importedProjectId: 103, seen: true }));
    expect(host.page!.content[1].importedProjectId).toBe(7);
    expect(host.importing.size).toBe(0);
    expect(bulk.busy).toBeFalse();
    expect(bulk.selected.size).toBe(0);
    expect(notifications.success).toHaveBeenCalledWith('offers.bulk.imported', 6000, { count: 57 });
  });

  it('says when every selected offer is in the pipeline already, and reports failures', () => {
    setUp('FEED', [offer(1, { importedProjectId: 9 }), offer(2)]);
    bulk.toggle(offer(1));
    bulk.act('add');
    expect(notifications.info).toHaveBeenCalledWith('offers.bulk.allInPipeline', 4000);

    offers.importOffers.and.returnValue(throwError(() => new Error('down')));
    bulk.toggle(offer(2));
    bulk.act('add');
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.import');
    expect(host.importing.size).toBe(0);

    offers.importOffers.and.returnValue(of({ imported: [], alreadyInPipeline: [{ offerId: 2, projectId: 5 }], missing: [] }));
    bulk.toggle(offer(2));
    bulk.act('add');
    expect(notifications.success).toHaveBeenCalledWith('offers.bulk.importedNone', 6000, { count: 0 });
  });
});

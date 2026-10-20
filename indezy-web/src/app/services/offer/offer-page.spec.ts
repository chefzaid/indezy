import { Offer, OfferPage } from '../../models/offer.models';
import { dropOffers, inChunks, patchOffers } from './offer-page';

describe('offer page helpers', () => {
  const offer = (id: number): Offer => ({ id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://example.test/${id}`, title: `Mission ${id}`,
    skills: [], publishedAt: '2026-10-20T08:00:00Z', datePrecision: 'EXACT', expired: false, saved: false, hidden: false, seen: false });
  const page: OfferPage = { content: [offer(1), offer(2), offer(3)], page: 0, size: 20, totalElements: 40, totalPages: 2,
    distanceAvailable: false, matchAvailable: false };

  it('changes some offers of the page and leaves the others as they are', () => {
    const changed = patchOffers(page, [1, 3, 9], { saved: true });

    expect(changed.content.map(item => item.saved)).toEqual([true, false, true]);
    expect(changed.content[1]).toBe(page.content[1]);
    expect(page.content[0].saved).toBeFalse();
  });

  it('drops offers and lowers the total by as many', () => {
    const kept = dropOffers(page, item => item.id !== 2);

    expect(kept.content.map(item => item.id)).toEqual([2]);
    expect(kept.totalElements).toBe(38);
    expect(dropOffers(page, () => false).totalElements).toBe(40);
  });

  it('splits items in runs', () => {
    expect(inChunks([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
    expect(inChunks([], 50)).toEqual([]);
  });
});

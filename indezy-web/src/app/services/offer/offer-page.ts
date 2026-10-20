import { Offer, OfferPage } from '../../models/offer.models';

/** The page with the given offers changed, so their cards redraw. */
export function patchOffers(page: OfferPage, ids: Iterable<number>, changes: Partial<Offer>): OfferPage {
  const changed = new Set(ids);
  return { ...page, content: page.content.map(offer => changed.has(offer.id) ? { ...offer, ...changes } : offer) };
}

/** The page without the offers `drop` picks, its total lowered by as many. */
export function dropOffers(page: OfferPage, drop: (offer: Offer) => boolean): OfferPage {
  const content = page.content.filter(offer => !drop(offer));
  return { ...page, content, totalElements: page.totalElements - (page.content.length - content.length) };
}

/** The items in runs of `size` at most. */
export function inChunks<T>(items: T[], size: number): T[][] {
  return Array.from({ length: Math.ceil(items.length / size) }, (_, index) => items.slice(index * size, (index + 1) * size));
}

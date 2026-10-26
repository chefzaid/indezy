import { IdSelection } from './id-selection';

describe('IdSelection', () => {
  it('picks and unpicks ids in order, ignoring unsaved records', () => {
    const selection = new IdSelection();

    selection.toggle(3);
    selection.toggle(1);
    selection.toggle(undefined);
    expect(selection.ids).toEqual([3, 1]);
    expect(selection.size).toBe(2);
    expect(selection.has(3)).toBe(true);
    expect(selection.has(undefined)).toBe(false);

    selection.toggle(3);
    expect(selection.ids).toEqual([1]);
    selection.clear();
    expect(selection.size).toBe(0);
  });

  it('picks a whole page, or leaves it when it was all picked', () => {
    const selection = new IdSelection();
    selection.toggle(9);
    selection.toggle(2);

    expect(selection.hasSome([1, 2, 3])).toBe(true);
    selection.toggleAll([1, 2, 3, undefined]);
    expect(selection.ids).toEqual([9, 2, 1, 3]);
    expect(selection.hasAll([1, 2, 3])).toBe(true);
    expect(selection.hasSome([1, 2, 3])).toBe(false);

    selection.toggleAll([1, 2, 3]);
    expect(selection.ids).toEqual([9]);
    expect(selection.hasAll([])).toBe(false);
  });

  it('forgets the ids that left the list', () => {
    const selection = new IdSelection();
    selection.toggleAll([1, 2, 3]);

    selection.retain([2, 3, 4]);

    expect(selection.ids).toEqual([2, 3]);
  });
});

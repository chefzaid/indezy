import { blankToNull } from './form-payload';

describe('blankToNull', () => {
  it('turns empty and whitespace-only strings into null', () => {
    expect(blankToNull({ workMode: '', rate: '  ', role: 'Dev', count: 0, flag: false }))
      .toEqual({ workMode: null, rate: null, role: 'Dev', count: 0, flag: false } as never);
  });

  it('keeps non-string values untouched', () => {
    const date = new Date(2026, 9, 15);
    const input = { date, list: ['a'], nested: { a: '' } };
    const output = blankToNull(input);
    expect(output.date).toBe(date);
    expect(output.list).toBe(input.list);
    expect(output.nested).toBe(input.nested);
  });
});

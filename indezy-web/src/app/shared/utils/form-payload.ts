/**
 * Converts blank form values ('' or whitespace-only strings) to null so optional numeric and
 * enum fields are sent as JSON null: the API rejects "" for Integer and enum properties.
 */
export function blankToNull<T extends object>(value: T): T {
  const result = { ...value } as Record<string, unknown>;
  for (const [key, entry] of Object.entries(result)) {
    if (typeof entry === 'string' && entry.trim() === '') {
      result[key] = null;
    }
  }
  return result as T;
}

/** Only accept local application paths, never an external or recursive login URL. */
export function loginDestination(value: string | null): string {
  if (!value?.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f]/.test(value)
      || /^\/(login|register)([/?#;]|$)/.test(value)) {
    return '/dashboard';
  }
  return value;
}

/** Offers the blob to the browser as a download under the given file name. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = globalThis.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  globalThis.URL.revokeObjectURL(url);
}

/** Offers the blob to the browser as a download under the given file name. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = globalThis.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  globalThis.URL.revokeObjectURL(url);
}

/** The extension of a copy from the freelancer's template: pdf for a filled PDF form, docx for a Word one. */
export function copyExtension(blob: Blob): 'pdf' | 'docx' {
  return blob.type === 'application/pdf' ? 'pdf' : 'docx';
}

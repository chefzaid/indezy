import { Page } from '@playwright/test';

/**
 * Waits for the open dialog's animations to end, its fade-in and those of the fields and menus inside it. A menu or
 * select panel that closes meanwhile cancels its own animation, which counts as ended.
 */
export async function settleDialog(page: Page): Promise<void> {
  await page.locator('.mat-mdc-dialog-container').evaluate(container =>
    Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => undefined))));
}

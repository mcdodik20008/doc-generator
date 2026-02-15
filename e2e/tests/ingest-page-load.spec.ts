import { test, expect } from '../fixtures/serve-app';
import { setupDefaultMocks, MOCK_EMPTY_DASHBOARD } from '../fixtures/mock-data';

test.describe('Ingest page load', () => {
  test('page title and header render', async ({ page, appURL }) => {
    await setupDefaultMocks(page);
    await page.goto(`${appURL}/ingest`);

    await expect(page).toHaveTitle('Doc Generator — Ingest');
    await expect(page.locator('h1')).toContainText('Ingest Pipeline');
  });

  test('loading spinner shows then hides after data loads', async ({ page, appURL }) => {
    // Delay the API response so we can see the spinner
    await page.route('**/api/dashboard/stats', async (route) => {
      await new Promise(r => setTimeout(r, 200));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          global: { totalApplications: 0, totalNodes: 0, totalChunks: 0, totalEdges: 0 },
          applications: [],
        }),
      });
    });

    await page.goto(`${appURL}/ingest`);

    // Spinner should be visible initially
    const loading = page.locator('#loading');
    // After data loads, spinner should be hidden
    await expect(loading).toBeHidden();
  });

  test('empty state when no applications', async ({ page, appURL }) => {
    await setupDefaultMocks(page, MOCK_EMPTY_DASHBOARD);
    await page.goto(`${appURL}/ingest`);

    await expect(page.locator('#emptyState')).toBeVisible();
    await expect(page.locator('#emptyState')).toContainText('No applications found');
    await expect(page.locator('#tableWrap')).toBeHidden();
  });
});

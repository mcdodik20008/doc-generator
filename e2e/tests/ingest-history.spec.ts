import { test, expect } from '../fixtures/serve-app';
import {
  setupDefaultMocks,
  MOCK_HISTORY_RUNS,
  MOCK_COMPLETED_RUN,
} from '../fixtures/mock-data';

test.describe('Ingest history', () => {
  test.beforeEach(async ({ page, appURL }) => {
    await setupDefaultMocks(page);

    // Mock history endpoint
    await page.route('**/api/ingest/runs/app/**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_HISTORY_RUNS),
      });
    });

    // Mock individual run endpoint
    await page.route('**/api/ingest/runs/*', (route) => {
      const url = route.request().url();
      if (url.includes('/app/') || url.includes('/stream')) return;
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_COMPLETED_RUN),
      });
    });

    // Mock SSE stream
    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: '',
      });
    });

    await page.goto(`${appURL}/ingest`);
    await expect(page.locator('#tableWrap')).toBeVisible();
  });

  test('history button shows history section with app name in title', async ({ page }) => {
    await page.locator('#appsBody tr').first().locator('.btn', { hasText: 'History' }).click();

    const section = page.locator('#historySection');
    await expect(section).toBeVisible();

    const title = page.locator('#historyTitle');
    await expect(title).toContainText('Run History');
    await expect(title).toContainText('My Service');
  });

  test('history table renders runs with correct columns', async ({ page }) => {
    await page.locator('#appsBody tr').first().locator('.btn', { hasText: 'History' }).click();

    await expect(page.locator('#historyTableWrap')).toBeVisible();

    const rows = page.locator('#historyBody tr');
    await expect(rows).toHaveCount(MOCK_HISTORY_RUNS.length);

    // First row is the completed run #41
    const firstRow = rows.first();
    await expect(firstRow).toContainText('#41');
    await expect(firstRow).toContainText('COMPLETED');
    await expect(firstRow).toContainText('main');
    await expect(firstRow).toContainText('abc12345');
  });

  test('view button loads pipeline for a past run', async ({ page }) => {
    await page.locator('#appsBody tr').first().locator('.btn', { hasText: 'History' }).click();
    await expect(page.locator('#historyTableWrap')).toBeVisible();

    // Click View on the first history row
    await page.locator('#historyBody tr').first().locator('.btn', { hasText: 'View' }).click();

    const pipelineSection = page.locator('#pipelineSection');
    await expect(pipelineSection).toBeVisible();
    await expect(pipelineSection).toContainText('Run #41');
  });

  test('empty history shows empty state', async ({ page }) => {
    // Override mock to return empty list for this test
    await page.route('**/api/ingest/runs/app/**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn', { hasText: 'History' }).click();

    await expect(page.locator('#historyEmpty')).toBeVisible();
    await expect(page.locator('#historyEmpty')).toContainText('No ingest runs found');
    await expect(page.locator('#historyTableWrap')).toBeHidden();
  });
});

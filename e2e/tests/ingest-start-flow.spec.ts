import { test, expect } from '../fixtures/serve-app';
import { setupDefaultMocks, MOCK_INGEST_RUN_STARTED, MOCK_COMPLETED_RUN } from '../fixtures/mock-data';

test.describe('Ingest start flow', () => {
  test.beforeEach(async ({ page, appURL }) => {
    await setupDefaultMocks(page);

    // Mock the SSE stream endpoint to return empty and close immediately
    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: '',
      });
    });

    // Mock the finalize run endpoint
    await page.route('**/api/ingest/runs/*', (route) => {
      if (route.request().url().includes('/stream')) return;
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_COMPLETED_RUN),
      });
    });

    await page.goto(`${appURL}/ingest`);
    await expect(page.locator('#tableWrap')).toBeVisible();
  });

  test('click Ingest sends POST to /api/ingest/start/{appId}', async ({ page }) => {
    let capturedUrl = '';
    let capturedMethod = '';

    await page.route('**/api/ingest/start/**', (route) => {
      capturedUrl = route.request().url();
      capturedMethod = route.request().method();
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_INGEST_RUN_STARTED),
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();

    expect(capturedMethod).toBe('POST');
    expect(capturedUrl).toContain('/api/ingest/start/1');
  });

  test('pipeline panel appears with run info', async ({ page }) => {
    await page.route('**/api/ingest/start/**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_INGEST_RUN_STARTED),
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();

    const panel = page.locator('#pipelineSection');
    await expect(panel).toBeVisible();
    await expect(panel).toContainText('Run #42');
    await expect(panel).toContainText('My Service');
  });

  test('success toast shows after starting ingest', async ({ page }) => {
    await page.route('**/api/ingest/start/**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_INGEST_RUN_STARTED),
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();

    const toast = page.locator('.toast-success');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('Ingest started');
  });

  test('409 conflict shows error toast', async ({ page }) => {
    await page.route('**/api/ingest/start/**', (route) => {
      route.fulfill({
        status: 409,
        contentType: 'text/plain',
        body: 'Ingest already running for this application',
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();

    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('Failed to start ingest');
  });
});

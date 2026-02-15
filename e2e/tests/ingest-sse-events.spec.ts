import { test, expect } from '../fixtures/serve-app';
import {
  setupDefaultMocks,
  MOCK_INGEST_RUN_STARTED,
  MOCK_COMPLETED_RUN,
  MOCK_SSE_EVENTS,
  buildSseStream,
} from '../fixtures/mock-data';

test.describe('Ingest SSE events', () => {
  test.beforeEach(async ({ page, appURL }) => {
    await setupDefaultMocks(page);

    // Mock POST start ingest
    await page.route('**/api/ingest/start/**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_INGEST_RUN_STARTED),
      });
    });

    // Mock finalize run endpoint (non-stream)
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

  test('SSE events populate event log entries', async ({ page }) => {
    const sseBody = buildSseStream(MOCK_SSE_EVENTS);

    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseBody,
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();
    await expect(page.locator('#pipelineSection')).toBeVisible();

    // Wait for log entries to appear
    const logEntries = page.locator('#eventLog .log-entry');
    await expect(logEntries).toHaveCount(MOCK_SSE_EVENTS.length, { timeout: 5000 });
  });

  test('log entries show level, step label, message', async ({ page }) => {
    const sseBody = buildSseStream(MOCK_SSE_EVENTS);

    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseBody,
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();

    const logEntries = page.locator('#eventLog .log-entry');
    await expect(logEntries).toHaveCount(MOCK_SSE_EVENTS.length, { timeout: 5000 });

    // Check first entry
    const firstEntry = logEntries.first();
    await expect(firstEntry.locator('.log-level')).toContainText('info');
    await expect(firstEntry.locator('.log-step')).toContainText('Checkout');
    await expect(firstEntry.locator('.log-msg')).toContainText('Starting checkout...');

    // Check warn level on 5th entry (index 4)
    const warnEntry = logEntries.nth(4);
    await expect(warnEntry.locator('.log-level')).toContainText('warn');
    await expect(warnEntry.locator('.log-msg')).toContainText('Some dependencies unresolved');
  });

  test('step state updates from SSE events', async ({ page }) => {
    const sseBody = buildSseStream(MOCK_SSE_EVENTS);

    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseBody,
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();
    await expect(page.locator('#pipelineSection')).toBeVisible();

    // Wait for SSE events to be processed
    await expect(page.locator('#eventLog .log-entry')).toHaveCount(MOCK_SSE_EVENTS.length, { timeout: 5000 });

    // After SSE events: CHECKOUT should be completed, RESOLVE_CLASSPATH should be completed
    const checkoutStep = page.locator('#step-CHECKOUT');
    await expect(checkoutStep).toHaveClass(/completed/);

    const classpathStep = page.locator('#step-RESOLVE_CLASSPATH');
    await expect(classpathStep).toHaveClass(/completed/);
  });

  test('clear log button works', async ({ page }) => {
    const sseBody = buildSseStream(MOCK_SSE_EVENTS);

    await page.route('**/api/ingest/runs/*/stream', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseBody,
      });
    });

    await page.locator('#appsBody tr').first().locator('.btn-accent').click();
    await expect(page.locator('#eventLog .log-entry')).toHaveCount(MOCK_SSE_EVENTS.length, { timeout: 5000 });

    // Click clear button
    await page.locator('button', { hasText: 'Clear' }).click();

    // Log entries should be gone, replaced by "Log cleared"
    await expect(page.locator('#eventLog .log-entry')).toHaveCount(0);
    await expect(page.locator('#eventLog .log-empty')).toContainText('Log cleared');
  });
});

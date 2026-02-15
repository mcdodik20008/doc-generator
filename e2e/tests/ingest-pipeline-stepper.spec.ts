import { test, expect } from '../fixtures/serve-app';
import { setupDefaultMocks, MOCK_COMPLETED_RUN, MOCK_FAILED_RUN, MOCK_INGEST_RUN_STARTED } from '../fixtures/mock-data';
import type { Page } from '@playwright/test';

const STEP_LABELS = ['Checkout', 'Classpath', 'Libraries', 'Graph', 'Link'];

/** Helper: show a pipeline by simulating startIngest with the given run mock. */
async function showPipelineForRun(page: Page, run: typeof MOCK_COMPLETED_RUN) {
  await page.route('**/api/ingest/start/**', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(run),
    });
  });

  // Mock SSE stream — return empty and close
  await page.route('**/api/ingest/runs/*/stream', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: '',
    });
  });

  // Mock finalize run endpoint
  await page.route('**/api/ingest/runs/*', (route) => {
    if (route.request().url().includes('/stream')) return;
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(run),
    });
  });

  await page.locator('#appsBody tr').first().locator('.btn-accent').click();
  await expect(page.locator('#pipelineSection')).toBeVisible();
}

test.describe('Ingest pipeline stepper', () => {
  test.beforeEach(async ({ page, appURL }) => {
    await setupDefaultMocks(page);
    await page.goto(`${appURL}/ingest`);
    await expect(page.locator('#tableWrap')).toBeVisible();
  });

  test('stepper renders 5 steps with labels', async ({ page }) => {
    await showPipelineForRun(page, MOCK_INGEST_RUN_STARTED);

    const steps = page.locator('.stepper .step-item');
    await expect(steps).toHaveCount(5);

    for (let i = 0; i < STEP_LABELS.length; i++) {
      await expect(steps.nth(i).locator('.step-label')).toContainText(STEP_LABELS[i]);
    }
  });

  test('completed run shows all steps green with checkmarks', async ({ page }) => {
    await showPipelineForRun(page, MOCK_COMPLETED_RUN);

    const steps = page.locator('.stepper .step-item');
    for (let i = 0; i < 5; i++) {
      await expect(steps.nth(i)).toHaveClass(/completed/);
      // Checkmark character ✓ (rendered from &#10003;)
      await expect(steps.nth(i).locator('.step-icon')).toContainText('✓');
    }
  });

  test('failed run shows failed + skipped steps correctly', async ({ page }) => {
    await showPipelineForRun(page, MOCK_FAILED_RUN);

    const steps = page.locator('.stepper .step-item');

    // First 3 steps completed
    for (let i = 0; i < 3; i++) {
      await expect(steps.nth(i)).toHaveClass(/completed/);
    }

    // BUILD_GRAPH failed
    await expect(steps.nth(3)).toHaveClass(/failed/);
    // LINK skipped
    await expect(steps.nth(4)).toHaveClass(/skipped/);
  });

  test('header shows status badge, branch, commit', async ({ page }) => {
    await showPipelineForRun(page, MOCK_COMPLETED_RUN);

    const header = page.locator('.pipeline-header');
    await expect(header.locator('.status-badge')).toContainText('COMPLETED');
    await expect(header).toContainText('main');
    await expect(header).toContainText('abc12345');
  });
});

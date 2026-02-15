import { test, expect } from '../fixtures/serve-app';
import { setupDefaultMocks, MOCK_DASHBOARD_STATS } from '../fixtures/mock-data';

test.describe('Ingest applications table', () => {
  test.beforeEach(async ({ page, appURL }) => {
    await setupDefaultMocks(page);
    await page.goto(`${appURL}/ingest`);
    await expect(page.locator('#tableWrap')).toBeVisible();
  });

  test('two rows render from mock data', async ({ page }) => {
    const rows = page.locator('#appsBody tr');
    await expect(rows).toHaveCount(2);
  });

  test('columns show correct data', async ({ page }) => {
    const firstRow = page.locator('#appsBody tr').first();

    // Name
    await expect(firstRow.locator('td').nth(0)).toContainText('My Service');
    // Key
    await expect(firstRow.locator('td').nth(1)).toContainText('my-service');
    // Status
    await expect(firstRow.locator('td').nth(3)).toContainText('success');
  });

  test('provider badges have correct CSS classes', async ({ page }) => {
    const firstProviderBadge = page.locator('#appsBody tr').first().locator('.provider-badge');
    await expect(firstProviderBadge).toHaveClass(/provider-github/);
    await expect(firstProviderBadge).toContainText('github');

    const secondProviderBadge = page.locator('#appsBody tr').nth(1).locator('.provider-badge');
    await expect(secondProviderBadge).toHaveClass(/provider-gitlab/);
    await expect(secondProviderBadge).toContainText('gitlab');
  });

  test('commit SHA truncated to 8 chars', async ({ page }) => {
    const shaCell = page.locator('#appsBody tr').first().locator('.sha-cell');
    // Full SHA is 'abc12345deadbeef', truncated to 'abc12345'
    await expect(shaCell).toHaveText('abc12345');
  });

  test('each row has Ingest and History buttons', async ({ page }) => {
    const rows = page.locator('#appsBody tr');
    const count = await rows.count();

    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const buttons = row.locator('.actions-cell .btn');
      await expect(buttons).toHaveCount(2);
      await expect(buttons.first()).toContainText('Ingest');
      await expect(buttons.nth(1)).toContainText('History');
    }
  });
});

import { expect as primitiveExpect, type Page, type Response } from '@playwright/test';

export const completedActivity = (page: Page, period: 'today' | 'all_time'): Promise<Response> =>
  page
    .waitForEvent('requestfinished', (request) => {
      const url = new URL(request.url());
      return (
        url.pathname === '/api/academic/dashboard/activity' &&
        url.searchParams.get('period') === period &&
        url.searchParams.get('bucket') === 'day' &&
        request.method() === 'GET'
      );
    })
    .then(async (request) => {
      const response = await request.response();
      primitiveExpect(response).not.toBeNull();
      return response!;
    });

type ActivityMetric = {
  state: 'AVAILABLE' | 'UNAVAILABLE' | 'INSUFFICIENT_HISTORY';
  value: number | null;
  percent: number | null;
  numerator: number | null;
  denominator: number | null;
  comparison: {
    state: string;
    absoluteDelta: number | null;
    percentPointDelta: number | null;
  } | null;
};
type Activity = {
  period: string;
  bucket: string;
  summary: { metrics: Record<string, ActivityMetric> };
};
export const assertActivity = async (page: Page, response: Response, period: 'today' | 'all_time', expect: typeof primitiveExpect = primitiveExpect) => {
  expect(response.status()).toBe(200);
  const activity = (await response.json()) as Activity;
  expect(
    activity.period === period && activity.bucket === 'day',
    'Actual selected activity period',
  ).toBe(true);
  const integer = new Intl.NumberFormat('ru-RU');
  const decimal = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
  for (const code of [
    'positiveMarks',
    'geoMarks',
    'mapOpens',
    'requests',
    'homeworkPublications',
  ]) {
    const metric = activity.summary.metrics[code];
    expect(['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY']).toContain(metric.state);
    const row = page.locator(`[data-admin-activity-metric="${code}"]`);
    await expect(row).toHaveAttribute('data-state', metric.state);
    const measured =
      metric.state === 'AVAILABLE' &&
      (code === 'geoMarks' ? metric.percent !== null : metric.value !== null);
    const expected = !measured
      ? '—'
      : code === 'geoMarks'
        ? `${decimal.format(metric.percent!)}%`
        : integer.format(metric.value!);
    await expect(row.locator('dd > span')).toHaveText(expected);
    const fraction = row.locator('dd > small').filter({ hasText: ' из ' });
    if (code === 'geoMarks' && metric.state === 'AVAILABLE') {
      expect(
        Number.isSafeInteger(metric.numerator) && Number.isSafeInteger(metric.denominator),
        'Actual geo absolute counts are known',
      ).toBe(true);
      await expect(fraction).toHaveText(
        `${integer.format(metric.numerator!)} из ${integer.format(metric.denominator!)}`,
      );
    } else await expect(fraction).toHaveCount(0);
    const delta =
      period === 'today' && measured && metric.comparison?.state === 'AVAILABLE'
        ? code === 'geoMarks'
          ? metric.comparison.percentPointDelta
          : metric.comparison.absoluteDelta
        : null;
    const deltaText =
      delta === null
        ? '—'
        : `${delta > 0 ? '+' : delta < 0 ? '−' : ''}${decimal.format(Math.abs(delta))}${code === 'geoMarks' ? '%' : ''}`;
    await expect(row.locator('.admin-dashboard-screen__delta')).toHaveText(deltaText);
    if (delta !== null)
      await expect(row.locator('.admin-dashboard-screen__delta')).toHaveAttribute(
        'aria-label',
        /относительно вчера/,
      );
  }
  await expect(
    page.getByRole('button', {
      name: period === 'today' ? 'Сегодня' : 'Всё время',
      exact: true,
    }),
  ).toHaveAttribute('aria-pressed', 'true');
};

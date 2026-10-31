import { expect as primitiveExpect, type Page, type Response, type TestInfo } from '@playwright/test';
import { retainFirstFailure } from './failed-case-diagnostics';

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
export const assertActivity = async (
  page: Page,
  response: Response,
  period: 'today' | 'all_time',
  expect: typeof primitiveExpect = primitiveExpect,
  info?: Pick<TestInfo, 'annotations' | 'title'>,
) => {
  const facts = {
    httpStatus: response.status(),
    jsonDecoded: false,
    activityReady: null as boolean | null,
    periodMatches: null as boolean | null,
    bucketMatches: null as boolean | null,
    metricStateKnown: null as boolean | null,
    geoCountsKnown: null as boolean | null,
    metricStateMatches: null as boolean | null,
    metricValueMatches: null as boolean | null,
    metricFractionMatches: null as boolean | null,
    metricDeltaMatches: null as boolean | null,
    selectedPeriodMatches: null as boolean | null,
  };
  const observe = async (
    key: 'activityReady' | 'metricStateMatches' | 'metricValueMatches' |
      'metricFractionMatches' | 'metricDeltaMatches' | 'selectedPeriodMatches',
    assertion: () => Promise<unknown>,
  ) => {
    try {
      await assertion();
      facts[key] = true;
    } catch (error) {
      facts[key] = false;
      throw error;
    }
  };
  try {
    expect(response.status()).toBe(200);
    const activity = (await response.json()) as Activity;
    facts.jsonDecoded = true;
    facts.periodMatches = activity.period === period;
    facts.bucketMatches = activity.bucket === 'day';
    expect(
      activity.period === period && activity.bucket === 'day',
      'Actual selected activity period',
    ).toBe(true);
    // Network completion precedes Vue's publication of the accepted activity cards.
    await observe('activityReady', () =>
      expect(page.locator('.admin-dashboard-screen__activity')).toHaveAttribute('aria-busy', 'false'),
    );
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
      facts.metricStateMatches = null;
      facts.metricValueMatches = null;
      facts.metricFractionMatches = null;
      facts.metricDeltaMatches = null;
      facts.geoCountsKnown = null;
      facts.metricStateKnown = !!metric &&
        ['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY'].includes(metric.state);
      expect(['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY']).toContain(metric?.state);
      const row = page.locator(`[data-admin-activity-metric="${code}"]`);
      await observe('metricStateMatches', () => expect(row).toHaveAttribute('data-state', metric.state));
      const measured =
        metric.state === 'AVAILABLE' &&
        (code === 'geoMarks' ? metric.percent !== null : metric.value !== null);
      const expected = !measured
        ? '—'
        : code === 'geoMarks'
          ? `${decimal.format(metric.percent!)}%`
          : integer.format(metric.value!);
      await observe('metricValueMatches', () => expect(row.locator('dd > span')).toHaveText(expected));
      const fraction = row.locator('dd > small').filter({ hasText: ' из ' });
      if (code === 'geoMarks' && metric.state === 'AVAILABLE') {
        facts.geoCountsKnown =
          Number.isSafeInteger(metric.numerator) && Number.isSafeInteger(metric.denominator);
        expect(
          Number.isSafeInteger(metric.numerator) && Number.isSafeInteger(metric.denominator),
          'Actual geo absolute counts are known',
        ).toBe(true);
        await observe('metricFractionMatches', () =>
          expect(fraction).toHaveText(
            `${integer.format(metric.numerator!)} из ${integer.format(metric.denominator!)}`,
          ),
        );
      } else await observe('metricFractionMatches', () => expect(fraction).toHaveCount(0));
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
      await observe('metricDeltaMatches', () =>
        expect(row.locator('.admin-dashboard-screen__delta')).toHaveText(deltaText),
      );
      if (delta !== null)
        await observe('metricDeltaMatches', () => expect(row.locator('.admin-dashboard-screen__delta')).toHaveAttribute(
          'aria-label',
          /относительно вчера/,
        ));
    }
    await observe('selectedPeriodMatches', () => expect(
      page.getByRole('button', {
        name: period === 'today' ? 'Сегодня' : 'Всё время',
        exact: true,
      }),
    ).toHaveAttribute('aria-pressed', 'true'));
  } catch (error) {
    if (info) retainFirstFailure(info, 'ADMIN_ACTIVITY', facts);
    throw error;
  }
};

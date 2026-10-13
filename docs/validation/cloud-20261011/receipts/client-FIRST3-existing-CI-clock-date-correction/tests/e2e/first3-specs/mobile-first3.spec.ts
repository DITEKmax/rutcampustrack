import { test } from '../fixtures/mobile-dataset';
import { FocusObserver } from '../fixtures/first3-observer';

let observer: FocusObserver | undefined;

// The same protected test object and hook order as the reviewed focused observer lane.
test.beforeEach(async ({ page, dataset, teachingInstant }, info) => {
  if (!teachingInstant) throw new Error('FIRST3 teaching instant is required');
  // Same educational day as the dataset; real timers and backend security clocks stay enabled.
  await page.clock.setFixedTime(teachingInstant);
  observer = new FocusObserver(page, dataset, info);
  observer.start();
});

test.afterEach(async ({}, info) => {
  const current = observer;
  observer = undefined;
  if (current) await current.finish(info);
});

// Register the unchanged accepted assertions; the explicit config selects only the first three.
require('../specs/mobile-real-server.spec.ts');

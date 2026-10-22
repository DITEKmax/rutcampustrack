import { test } from '../fixtures/mobile-dataset';
import { FocusObserver } from '../fixtures/first3-observer';

let observer: FocusObserver | undefined;

// The same protected test object and hook order as the reviewed focused observer lane.
test.beforeEach(async ({ page, dataset }, info) => {
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

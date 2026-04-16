import 'zone.js';
import '@angular/compiler';
import '@testing-library/jest-dom';
import { afterEach } from 'vitest';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserTestingModule,
  platformBrowserTesting,
} from '@angular/platform-browser/testing';

getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting(), {
  teardown: { destroyAfterEach: true },
});

// Isolate persisted state (e.g. auth tokens in rct.auth.v1) between specs so
// stateful services like AuthService don't leak across test boundaries.
afterEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

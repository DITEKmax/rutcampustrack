import type { Page, Request, Response } from '@playwright/test';

/** Only the Today refresh emitted by this action, never an older pending read. */
export async function todayAfterAction(
  page: Page,
  action: () => Promise<unknown>,
  intercepted?: () => Request | undefined,
  timeout = 30000,
): Promise<Response> {
  const frame = page.mainFrame();
  const origin = new URL(page.url()).origin;
  let armed = false;
  let owned: Request | undefined;
  let resolve!: (response: Response) => void;
  let reject!: (error: Error) => void;
  const pending = new Promise<Response>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  void pending.catch(() => undefined);
  let cancel!: (error: Error) => void;
  const cancelled = new Promise<never>((_, no) => {
    cancel = no;
  });
  void cancelled.catch(() => undefined);
  const abort = (error: Error) => {
    reject(error);
    cancel(error);
  };
  const requested = (request: Request) => {
    if (!armed || owned) return;
    try {
      const url = new URL(request.url());
      if (
        request.frame() === frame &&
        request.method() === 'GET' &&
        url.origin === origin &&
        url.pathname === '/api/v1/student/today'
      )
        owned = request;
    } catch {
      /* Worker requests cannot own this page action. */
    }
  };
  const responded = (response: Response) => {
    if (response.request() === owned && (!intercepted || response.request() === intercepted()))
      resolve(response);
  };
  const failed = (request: Request) => {
    if (request === owned) abort(new Error('Owned Today request failed'));
  };
  const closed = () => abort(new Error('Owned Today page closed'));
  page.on('request', requested);
  page.on('response', responded);
  page.on('requestfailed', failed);
  page.on('close', closed);
  const timer = setTimeout(() => abort(new Error('Owned Today response deadline')), timeout);
  try {
    armed = true;
    const performed = action();
    const joined = Promise.all([performed, pending]).then(([, response]) => response);
    return await Promise.race([joined, cancelled]);
  } finally {
    armed = false;
    clearTimeout(timer);
    page.off('request', requested);
    page.off('response', responded);
    page.off('requestfailed', failed);
    page.off('close', closed);
    owned = undefined;
  }
}

import type { SpeedTestEvent } from '../types';

const listeners: Array<(e: SpeedTestEvent) => void> = [];
let rejectRun: ((e: unknown) => void) | undefined;
const emit = (e: SpeedTestEvent) => listeners.forEach((l) => l(e));
const result = { download: 90, upload: 40, ping: 12, jitter: 1.5 };

const native = {
  startTest: jest.fn(() => new Promise((resolve, reject) => {
    rejectRun = reject;
    (async () => {
      for (const phase of ['connecting', 'ping', 'probe', 'download', 'upload'] as const) emit({ phase });
      if (!rejectRun) return;
      emit({ phase: 'finished', result });
      resolve(result);
    })();
  })),
  stopTest: jest.fn(() => { rejectRun?.(Object.assign(new Error('cancelled'), { code: 'CANCELLED' })); rejectRun = undefined; }),
  addListener: jest.fn((_n: string, cb: (e: SpeedTestEvent) => void) => {
    listeners.push(cb);
    return { remove: () => listeners.splice(listeners.indexOf(cb), 1) };
  }),
};
jest.mock('../NativeModule', () => ({ __esModule: true, default: native }));

import { addStateListener, startTest, stopTest } from '../index';

beforeEach(() => { listeners.length = 0; jest.clearAllMocks(); });

test('emits phases in order and resolves with result', async () => {
  const phases: string[] = [];
  addStateListener((e) => phases.push(e.phase));
  const r = await startTest({ baseUrl: 'https://x' });
  expect(phases).toEqual(['connecting', 'ping', 'probe', 'download', 'upload', 'finished']);
  expect(r).toEqual(result);
});

test('stopTest calls native and promise rejects CANCELLED', async () => {
  native.startTest.mockImplementationOnce(() => new Promise((_, reject) => { rejectRun = reject; }));
  const p = startTest({ baseUrl: 'https://x' });
  stopTest();
  expect(native.stopTest).toHaveBeenCalled();
  await expect(p).rejects.toMatchObject({ code: 'CANCELLED' });
});

test('rejects without baseUrl', async () => {
  await expect(startTest({} as never)).rejects.toMatchObject({ code: 'INVALID_CONFIG' });
});

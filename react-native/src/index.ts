import Native from './NativeModule';
import type { SpeedTestConfig, SpeedTestEvent, SpeedTestResult, Subscription } from './types';

export * from './types';

const EVENT = 'onStateChange';
type Emitter = { addListener(name: string, cb: (e: SpeedTestEvent) => void): Subscription };

export function startTest(config: SpeedTestConfig): Promise<SpeedTestResult> {
  if (!config || !config.baseUrl) return Promise.reject(Object.assign(new Error('baseUrl is required'), { code: 'INVALID_CONFIG' }));
  return Native.startTest(config);
}

export function stopTest(): void {
  Native.stopTest();
}

export function addStateListener(cb: (e: SpeedTestEvent) => void): Subscription {
  return (Native as unknown as Emitter).addListener(EVENT, cb);
}

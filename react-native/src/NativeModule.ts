import { requireNativeModule } from 'expo-modules-core';
import type { SpeedTestConfig, SpeedTestResult } from './types';

export interface NativeSpeedTestModule {
  startTest(config: SpeedTestConfig): Promise<SpeedTestResult>;
  stopTest(): void;
  addListener(eventName: string): { remove(): void } | void;
}

export default requireNativeModule<NativeSpeedTestModule>('FastlinkSpeedTest');

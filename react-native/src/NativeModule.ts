import { requireNativeModule } from 'expo-modules-core';
import type { SpeedTestConfig, SpeedTestEvent, SpeedTestResult, Subscription } from './types';

export interface NativeSpeedTestModule {
  startTest(config: SpeedTestConfig): Promise<SpeedTestResult>;
  stopTest(): void;
  addListener(eventName: string, listener: (e: SpeedTestEvent) => void): Subscription;
}

export default requireNativeModule<NativeSpeedTestModule>('FastlinkSpeedTest');

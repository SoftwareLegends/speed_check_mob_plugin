export type SpeedTestPhase = 'idle'|'connecting'|'ping'|'probe'|'download'|'upload'|'finished'|'error'|'cancelled';

export interface SpeedTestConfig {
  baseUrl: string;
  username?: string;
  password?: string;
  pingTimeoutMs?: number;
  downloadTimeoutMs?: number;
  uploadTimeoutMs?: number;
  overallTimeoutMs?: number;
  /** Minimum interval between progress events (ms). */
  reportIntervalMs?: number;
}

export interface SpeedTestResult {
  download: number; // Mbps
  upload: number;   // Mbps
  ping: number;     // ms
  jitter: number;   // ms
  downloadPeak?: number;
  uploadPeak?: number;
  serverUrl?: string;
  timestamp?: number;
}

export interface SpeedTestError { phase: string; code: string; message: string }

export interface SpeedTestEvent {
  phase: SpeedTestPhase;
  currentMbps?: number;
  peakMbps?: number;
  progress?: number;
  pingMs?: number;
  jitterMs?: number;
  result?: SpeedTestResult;
  error?: SpeedTestError;
}

export interface Subscription { remove(): void }

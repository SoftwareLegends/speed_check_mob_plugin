# fastlink-speedtest-expo (Android)

Expo module wrapping `fastlink-speedtest-core`.

```ts
import { startTest, stopTest, addStateListener } from 'fastlink-speedtest-expo';
const sub = addStateListener((e) => console.log(e.phase, e.currentMbps));
const r = await startTest({ baseUrl, username, password, overallTimeoutMs: 180000 }); // {download, upload, ping, jitter}
stopTest(); // rejects startTest with code CANCELLED
sub.remove();
```
Config comes from the caller at runtime; never commit credentials. `reportIntervalMs` throttles progress events.

## Native dependency
- Development: include this repo's root project in the app (`include(":fastlink-speedtest-core")`, projectDir -> repo root); the module uses the local project.
- Consumers: JitPack `com.github.SoftwareLegends.speed_check_mob_plugin:fastlink-speedtest-core:<FASTLINK_VERSION>` (add `maven { url 'https://jitpack.io' }`).

Not build-verified in an Expo app yet (no emulator/Expo app on the build server); Kotlin mapping logic is unit-tested in core (`SpeedTestEventMapperTest`), TS with Jest.

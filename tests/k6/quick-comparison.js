import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const mvcErrors = new Rate('mvc_errors');
const webfluxErrors = new Rate('webflux_errors');
const webfluxBlockingErrors = new Rate('webflux_blocking_errors');
const mvcDuration = new Trend('mvc_duration');
const webfluxDuration = new Trend('webflux_duration');
const webfluxBlockingDuration = new Trend('webflux_blocking_duration');

// Shorter test for quick comparison
export const options = {
    scenarios: {
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 30 },   // Ramp up
                { duration: '15s', target: 30 },  // Stay at 30 users
                { duration: '5s', target: 50 },   // Ramp to 50
                { duration: '15s', target: 50 },  // Stay at 50 users
                { duration: '5s', target: 0 },    // Ramp down
            ],
        },
    },
};

const MVC_URL = __ENV.MVC_URL || 'http://localhost:8080';
const WEBFLUX_URL = __ENV.WEBFLUX_URL || 'http://localhost:8081';
const DELAY_MS = __ENV.DELAY_MS || '200';

export default function () {
    // Test MVC (blocking)
    const mvcRes = http.get(`${MVC_URL}/api/v1/delay/${DELAY_MS}`, { tags: { name: 'MVC' } });
    const mvcSuccess = check(mvcRes, { 'mvc status 200': (r) => r.status === 200 });
    mvcErrors.add(!mvcSuccess);
    if (mvcRes.status === 200) {
        mvcDuration.add(mvcRes.timings.duration);
    }

    // Test WebFlux (non-blocking)
    const webfluxRes = http.get(`${WEBFLUX_URL}/api/v1/delay/${DELAY_MS}`, { tags: { name: 'WebFlux' } });
    const webfluxSuccess = check(webfluxRes, { 'webflux status 200': (r) => r.status === 200 });
    webfluxErrors.add(!webfluxSuccess);
    if (webfluxRes.status === 200) {
        webfluxDuration.add(webfluxRes.timings.duration);
    }

    // Test WebFlux with blocking code (anti-pattern)
    const blockingRes = http.get(`${WEBFLUX_URL}/api/v1/delay/blocking/${DELAY_MS}`, { tags: { name: 'WebFlux-Blocking' } });
    const blockingSuccess = check(blockingRes, { 'blocking status 200': (r) => r.status === 200 });
    webfluxBlockingErrors.add(!blockingSuccess);
    if (blockingRes.status === 200) {
        webfluxBlockingDuration.add(blockingRes.timings.duration);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    const metrics = data.metrics;

    const getMvcMetrics = () => ({
        avg: metrics.mvc_duration?.values?.avg || 0,
        min: metrics.mvc_duration?.values?.min || 0,
        max: metrics.mvc_duration?.values?.max || 0,
        p90: metrics.mvc_duration?.values?.['p(90)'] || 0,
        p95: metrics.mvc_duration?.values?.['p(95)'] || 0,
        errors: (metrics.mvc_errors?.values?.rate || 0) * 100,
    });

    const getWebfluxMetrics = () => ({
        avg: metrics.webflux_duration?.values?.avg || 0,
        min: metrics.webflux_duration?.values?.min || 0,
        max: metrics.webflux_duration?.values?.max || 0,
        p90: metrics.webflux_duration?.values?.['p(90)'] || 0,
        p95: metrics.webflux_duration?.values?.['p(95)'] || 0,
        errors: (metrics.webflux_errors?.values?.rate || 0) * 100,
    });

    const getBlockingMetrics = () => ({
        avg: metrics.webflux_blocking_duration?.values?.avg || 0,
        min: metrics.webflux_blocking_duration?.values?.min || 0,
        max: metrics.webflux_blocking_duration?.values?.max || 0,
        p90: metrics.webflux_blocking_duration?.values?.['p(90)'] || 0,
        p95: metrics.webflux_blocking_duration?.values?.['p(95)'] || 0,
        errors: (metrics.webflux_blocking_errors?.values?.rate || 0) * 100,
    });

    const mvc = getMvcMetrics();
    const webflux = getWebfluxMetrics();
    const blocking = getBlockingMetrics();

    const summary = `
================================================================================
                    PERFORMANCE COMPARISON RESULTS
================================================================================

Delay: ${__ENV.DELAY_MS || '200'}ms | VUs: max ${metrics.vus_max?.values?.max || 0}

--------------------------------------------------------------------------------
                     MVC                WebFlux           WebFlux+Blocking
                  (Thread.sleep)      (delay())          (Thread.sleep)
--------------------------------------------------------------------------------
  avg             ${mvc.avg.toFixed(2).padStart(10)}ms    ${webflux.avg.toFixed(2).padStart(10)}ms    ${blocking.avg.toFixed(2).padStart(10)}ms
  min             ${mvc.min.toFixed(2).padStart(10)}ms    ${webflux.min.toFixed(2).padStart(10)}ms    ${blocking.min.toFixed(2).padStart(10)}ms
  max             ${mvc.max.toFixed(2).padStart(10)}ms    ${webflux.max.toFixed(2).padStart(10)}ms    ${blocking.max.toFixed(2).padStart(10)}ms
  p(90)           ${mvc.p90.toFixed(2).padStart(10)}ms    ${webflux.p90.toFixed(2).padStart(10)}ms    ${blocking.p90.toFixed(2).padStart(10)}ms
  p(95)           ${mvc.p95.toFixed(2).padStart(10)}ms    ${webflux.p95.toFixed(2).padStart(10)}ms    ${blocking.p95.toFixed(2).padStart(10)}ms
  errors          ${mvc.errors.toFixed(2).padStart(10)}%    ${webflux.errors.toFixed(2).padStart(10)}%    ${blocking.errors.toFixed(2).padStart(10)}%
--------------------------------------------------------------------------------

Total Requests: ${metrics.http_reqs?.values?.count || 0}
Request Rate: ${(metrics.http_reqs?.values?.rate || 0).toFixed(2)}/s

================================================================================
                           KEY INSIGHTS
================================================================================

1. MVC (Thread.sleep):
   - Each request blocks a thread from the thread pool
   - Limited by thread pool size (default ~200 threads)
   - Predictable latency under load

2. WebFlux (delay):
   - Non-blocking: threads released during I/O wait
   - Can handle many more concurrent connections
   - Lower latency at high concurrency

3. WebFlux + Thread.sleep (ANTI-PATTERN):
   - Blocks the event loop threads
   - WebFlux has fewer threads than MVC (typically CPU cores)
   - WORST performance - defeats the purpose of reactive

================================================================================
`;

    return {
        stdout: summary,
        'results/comparison-results.json': JSON.stringify(data, null, 2),
    };
}

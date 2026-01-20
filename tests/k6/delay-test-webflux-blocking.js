import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

/**
 * This test demonstrates the ANTI-PATTERN of using blocking code in WebFlux.
 * It calls the /api/v1/delay/blocking/{ms} endpoint which uses Thread.sleep()
 * instead of coroutine delay().
 *
 * Expected result: Performance should be similar or WORSE than MVC
 * because blocking the event loop threads is catastrophic for WebFlux.
 */

// Custom metrics
const errorRate = new Rate('errors');
const delayDuration = new Trend('delay_duration');

// Test configuration
export const options = {
    scenarios: {
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '30s', target: 50 },
                { duration: '10s', target: 100 },
                { duration: '30s', target: 100 },
                { duration: '10s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'], // Relaxed threshold - blocking will cause issues
        errors: ['rate<0.5'],               // Expect more errors
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const DELAY_MS = __ENV.DELAY_MS || '500';

export default function () {
    // Test the BLOCKING delay endpoint (anti-pattern)
    const response = http.get(`${BASE_URL}/api/v1/delay/blocking/${DELAY_MS}`);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
    });

    errorRate.add(!success);

    if (response.status === 200) {
        const body = JSON.parse(response.body);
        delayDuration.add(body.actualDelay);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/webflux-blocking-test.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data, opts) {
    const metrics = data.metrics;

    let summary = `
================================================================================
WEBFLUX BLOCKING TEST RESULTS (ANTI-PATTERN - Thread.sleep in WebFlux)
================================================================================

⚠️  WARNING: This demonstrates what happens when you use blocking code in WebFlux!

HTTP Request Duration:
  - avg: ${(metrics.http_req_duration.values.avg || 0).toFixed(2)}ms
  - min: ${(metrics.http_req_duration.values.min || 0).toFixed(2)}ms
  - max: ${(metrics.http_req_duration.values.max || 0).toFixed(2)}ms
  - p(90): ${(metrics.http_req_duration.values['p(90)'] || 0).toFixed(2)}ms
  - p(95): ${(metrics.http_req_duration.values['p(95)'] || 0).toFixed(2)}ms

Requests:
  - total: ${metrics.http_reqs.values.count}
  - rate: ${(metrics.http_reqs.values.rate || 0).toFixed(2)}/s

Errors: ${((metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%

Virtual Users:
  - max: ${metrics.vus_max?.values?.max || 0}

⚠️  Compare these results with the non-blocking WebFlux test!
================================================================================
`;

    return summary;
}

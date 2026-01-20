import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const customDuration = new Trend('custom_duration');

// High load test - up to 200 concurrent users
export const options = {
    scenarios: {
        high_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 50 },
                { duration: '10s', target: 50 },
                { duration: '5s', target: 100 },
                { duration: '10s', target: 100 },
                { duration: '5s', target: 150 },
                { duration: '10s', target: 150 },
                { duration: '5s', target: 0 },
            ],
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DELAY_MS = __ENV.DELAY_MS || '200';
const ENDPOINT = __ENV.ENDPOINT || 'delay';

export default function () {
    let url;
    if (ENDPOINT === 'blocking') {
        url = `${BASE_URL}/api/v1/delay/blocking/${DELAY_MS}`;
    } else {
        url = `${BASE_URL}/api/v1/delay/${DELAY_MS}`;
    }

    const res = http.get(url);

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
    if (res.status === 200) {
        customDuration.add(res.timings.duration);
    }

    sleep(0.05); // Minimal think time
}

export function handleSummary(data) {
    const metrics = data.metrics;
    const endpoint = __ENV.ENDPOINT || 'delay';
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    const isWebflux = baseUrl.includes('8081');
    const testName = isWebflux
        ? (endpoint === 'blocking' ? 'WebFlux + Thread.sleep (ANTI-PATTERN)' : 'WebFlux (Non-blocking delay)')
        : 'MVC (Blocking Thread.sleep)';

    const summary = `
================================================================================
${testName}
================================================================================
URL: ${baseUrl}/api/v1/delay/${endpoint === 'blocking' ? 'blocking/' : ''}${__ENV.DELAY_MS || '200'}

Response Time:
  avg:     ${(metrics.custom_duration?.values?.avg || 0).toFixed(2)}ms
  min:     ${(metrics.custom_duration?.values?.min || 0).toFixed(2)}ms
  max:     ${(metrics.custom_duration?.values?.max || 0).toFixed(2)}ms
  p(90):   ${(metrics.custom_duration?.values?.['p(90)'] || 0).toFixed(2)}ms
  p(95):   ${(metrics.custom_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
  p(99):   ${(metrics.custom_duration?.values?.['p(99)'] || 0).toFixed(2)}ms

Throughput:
  Total Requests: ${metrics.http_reqs?.values?.count || 0}
  Request Rate:   ${(metrics.http_reqs?.values?.rate || 0).toFixed(2)} req/s

Errors: ${((metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%
Max VUs: ${metrics.vus_max?.values?.max || 0}
================================================================================
`;

    return {
        stdout: summary,
    };
}

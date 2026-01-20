import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const delayDuration = new Trend('delay_duration');

// Test configuration
export const options = {
    scenarios: {
        // Ramp up to 100 concurrent users
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },   // Ramp up to 50 users
                { duration: '30s', target: 50 },   // Stay at 50 users
                { duration: '10s', target: 100 },  // Ramp up to 100 users
                { duration: '30s', target: 100 },  // Stay at 100 users
                { duration: '10s', target: 0 },    // Ramp down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'], // 95% of requests should be under 3s
        errors: ['rate<0.1'],               // Error rate should be under 10%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DELAY_MS = __ENV.DELAY_MS || '500';

export default function () {
    // Test the blocking delay endpoint
    const response = http.get(`${BASE_URL}/api/v1/delay/${DELAY_MS}`);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has requestedDelay': (r) => {
            const body = JSON.parse(r.body);
            return body.requestedDelay !== undefined;
        },
    });

    errorRate.add(!success);

    if (response.status === 200) {
        const body = JSON.parse(response.body);
        delayDuration.add(body.actualDelay);

        // Log thread info occasionally
        if (Math.random() < 0.01) {
            console.log(`MVC Thread: ${body.threadName}, Delay: ${body.actualDelay}ms`);
        }
    }

    // Small think time
    sleep(0.1);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/mvc-delay-test.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data, opts) {
    const metrics = data.metrics;

    let summary = `
================================================================================
MVC DELAY TEST RESULTS (Blocking - Thread.sleep)
================================================================================

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
================================================================================
`;

    return summary;
}

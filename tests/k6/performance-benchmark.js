import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    sleep_test: {
      executor: 'constant-vus',
      vus: 100,
      duration: '1m',
      exec: 'sleepTask',
    },
    bulk_test: {
      executor: 'constant-vus',
      vus: 100,
      duration: '1m',
      startTime: '1m',
      exec: 'bulkTask',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8081/api/v1/performance';

export function sleepTask() {
  const res = http.get(`${BASE_URL}/sleep`);
  check(res, {
    'is status 200': (r) => r.status === 200,
    'response time < 1.5s': (r) => r.timings.duration < 1500,
  });
}

export function bulkTask() {
  const res = http.get(`${BASE_URL}/bulk`);
  check(res, {
    'is status 200': (r) => r.status === 200,
    'body size > 0': (r) => r.body.length > 0,
  });
}

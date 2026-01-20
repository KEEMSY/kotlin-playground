#!/bin/bash

# Performance Comparison Test Script
# Compares MVC (blocking) vs WebFlux (non-blocking) vs WebFlux with blocking code

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
DELAY_MS="${DELAY_MS:-500}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Performance Comparison Test Suite  ${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo "Configuration:"
echo "  - MVC URL: ${MVC_URL:-http://localhost:8080}"
echo "  - WebFlux URL: ${WEBFLUX_URL:-http://localhost:8081}"
echo "  - Delay: ${DELAY_MS}ms"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 is not installed.${NC}"
    echo "Install k6: https://k6.io/docs/getting-started/installation/"
    echo ""
    echo "macOS: brew install k6"
    echo "Docker: docker run -i grafana/k6 run - <script.js"
    exit 1
fi

# Check if services are running
echo -e "${YELLOW}Checking if services are running...${NC}"

MVC_URL="${MVC_URL:-http://localhost:8080}"
WEBFLUX_URL="${WEBFLUX_URL:-http://localhost:8081}"

if ! curl -s "${MVC_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}Warning: MVC service (${MVC_URL}) is not responding${NC}"
    echo "Please start the service first:"
    echo "  ./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local'"
fi

if ! curl -s "${WEBFLUX_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}Warning: WebFlux service (${WEBFLUX_URL}) is not responding${NC}"
    echo "Please start the service first:"
    echo "  ./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local'"
fi

echo ""
echo -e "${GREEN}Starting tests...${NC}"
echo ""

# Run MVC test
echo -e "${BLUE}[1/3] Running MVC (blocking) test...${NC}"
cd "$SCRIPT_DIR"
k6 run --env BASE_URL="$MVC_URL" --env DELAY_MS="$DELAY_MS" delay-test-mvc.js

echo ""
echo -e "${BLUE}[2/3] Running WebFlux (non-blocking) test...${NC}"
k6 run --env BASE_URL="$WEBFLUX_URL" --env DELAY_MS="$DELAY_MS" delay-test-webflux.js

echo ""
echo -e "${BLUE}[3/3] Running WebFlux blocking (anti-pattern) test...${NC}"
k6 run --env BASE_URL="$WEBFLUX_URL" --env DELAY_MS="$DELAY_MS" delay-test-webflux-blocking.js

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  All tests completed!               ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo "Results saved to: ${RESULTS_DIR}"
echo ""
echo -e "${YELLOW}Key observations to look for:${NC}"
echo "1. MVC: Limited throughput due to thread pool exhaustion"
echo "2. WebFlux (non-blocking): High throughput, threads released during I/O"
echo "3. WebFlux (blocking): Poor performance - event loop threads blocked"
echo ""

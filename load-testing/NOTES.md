# load-testing (not part of this phase)

Reserved for k6 / JMeter / Locust scripts that would demonstrate, with
real numbers, the impact of:
- Redis response caching (latency: cache hit vs cache miss)
- The token-bucket rate limiter (watch 429s appear under burst load)
- Round-robin load balancing across the two product-service instances
- The circuit breaker tripping open under induced downstream failure

Not required for the current feature set - everything above can already
be demonstrated manually via curl/Postman and the Grafana dashboard
(see README.md), a proper load-testing script is a natural next step if
you want to show real throughput numbers in your placement demo.

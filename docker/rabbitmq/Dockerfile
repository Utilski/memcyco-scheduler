FROM rabbitmq:4-alpine
# Supply a non-guest RABBITMQ_DEFAULT_USER and RABBITMQ_DEFAULT_PASS at runtime.
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
    CMD rabbitmq-diagnostics -q ping || exit 1

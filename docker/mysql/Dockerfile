FROM mysql:8.4
# The official image owns initialization, permissions, and /var/lib/mysql persistence.
# Supply MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD at runtime.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD mysqladmin ping --host=127.0.0.1 --silent || exit 1

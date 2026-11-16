#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PROJECT_VERSION="${PROJECT_VERSION:-0.1.0-SNAPSHOT}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-90}"
JAVA_CMD="${JAVA_CMD:-java}"

# 优先使用Maven Wrapper；其次使用MVN_CMD、系统Maven或IDE自带Maven。
IDE_MAVEN="$HOME/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"
if [[ -x "$ROOT_DIR/mvnw" ]]; then
    MVN_CMD="${MVN_CMD:-$ROOT_DIR/mvnw}"
elif [[ -n "${MVN_CMD:-}" ]]; then
    : # 保留调用方显式指定的Maven路径。
elif command -v mvn >/dev/null 2>&1; then
    MVN_CMD="mvn"
elif [[ -x "$IDE_MAVEN" ]]; then
    MVN_CMD="$IDE_MAVEN"
else
    MVN_CMD="mvn"
fi

# 业务服务按端口顺序启动，网关始终最后启动。
SERVICES=(
    "cart-service:3001"
    "order-service:3002"
    "pay-service:3003"
    "product-service:3004"
    "search-service:3005"
    "stock-service:3006"
    "user-service:3007"
)
GATEWAY_SERVICE="gateway-service"
GATEWAY_PORT=3000
LOG_DIR="$ROOT_DIR/target/dev-logs"
PIDS=()
LAST_PID=""

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "缺少命令: $1" >&2
        exit 1
    fi
}

port_in_use() {
    (echo >"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

cleanup() {
    local status=$?
    trap - EXIT
    if [[ ${#PIDS[@]} -gt 0 ]]; then
        echo
        echo "正在停止本次脚本启动的服务..."
        for pid in "${PIDS[@]}"; do
            kill "$pid" >/dev/null 2>&1 || true
        done
        for pid in "${PIDS[@]}"; do
            wait "$pid" >/dev/null 2>&1 || true
        done
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_ready() {
    local name=$1
    local port=$2
    local pid=$3
    local elapsed=0
    local http_status

    while ((elapsed < STARTUP_TIMEOUT)); do
        if ! kill -0 "$pid" >/dev/null 2>&1; then
            echo "$name 启动失败，最近日志如下：" >&2
            tail -n 60 "$LOG_DIR/$name.log" >&2 || true
            return 1
        fi

        # 健康检查未开放或需要登录时，302/401/403/404也能证明HTTP服务已就绪。
        http_status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 \
            "http://127.0.0.1:$port/actuator/health" 2>/dev/null || true)
        if [[ "$http_status" =~ ^(200|302|401|403|404)$ ]]; then
            echo "$name 已就绪，端口: $port"
            return 0
        fi

        sleep 1
        ((elapsed += 1))
    done

    echo "$name 在 ${STARTUP_TIMEOUT}s 内未就绪，最近日志如下：" >&2
    tail -n 60 "$LOG_DIR/$name.log" >&2 || true
    return 1
}

start_service() {
    local name=$1
    local port=$2
    local jar="$ROOT_DIR/$name/target/$name-$PROJECT_VERSION.jar"

    if [[ ! -f "$jar" ]]; then
        echo "找不到可执行JAR: $jar" >&2
        exit 1
    fi

    echo "正在启动 $name（端口 $port），日志: $LOG_DIR/$name.log"
    "$JAVA_CMD" -jar "$jar" >"$LOG_DIR/$name.log" 2>&1 &
    LAST_PID=$!
    PIDS+=("$LAST_PID")
    wait_for_ready "$name" "$port" "$LAST_PID"
}

require_command "$JAVA_CMD"
require_command "$MVN_CMD"
require_command curl

# 不占用或终止已有进程；端口冲突时提前退出。
for service in "${SERVICES[@]}" "$GATEWAY_SERVICE:$GATEWAY_PORT"; do
    IFS=: read -r name port <<<"$service"
    if port_in_use "$port"; then
        echo "端口 $port 已被占用（$name 需要该端口），请先停止对应进程后重试。" >&2
        exit 1
    fi
done

mkdir -p "$LOG_DIR"

echo "正在构建全部服务..."
"$MVN_CMD" -DskipTests package

for service in "${SERVICES[@]}"; do
    IFS=: read -r name port <<<"$service"
    start_service "$name" "$port"
done

# 所有业务服务就绪后再启动统一入口。
start_service "$GATEWAY_SERVICE" "$GATEWAY_PORT"
GATEWAY_PID=$LAST_PID

cat <<EOF

全部服务已启动。
统一入口: http://127.0.0.1:$GATEWAY_PORT/api/v1
健康检查: http://127.0.0.1:$GATEWAY_PORT/actuator/health
日志目录: $LOG_DIR

按 Ctrl+C 可停止本次脚本启动的全部服务。
EOF

# 网关退出或异常时触发EXIT清理，避免遗留其他业务进程。
wait "$GATEWAY_PID"

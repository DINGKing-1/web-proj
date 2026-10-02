#!/bin/bash
# ============================================
#  优品汇 - 停止脚本
# ============================================

cd "$(dirname "$0")"

PORT=8080
PROJECT_DIR="$(pwd)"

echo "=============================="
echo "  正在停止 优品汇 服务..."
echo "=============================="

# 方式1：读取 PID 文件（如果存在）
if [ -f "$PROJECT_DIR/.app.pid" ]; then
    PID=$(cat "$PROJECT_DIR/.app.pid")
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        echo "[1] 发送优雅关闭信号（PID: $PID）..."
        kill "$PID" 2>/dev/null
        # 等待 graceful shutdown（最多 20 秒）
        for i in $(seq 1 20); do
            if ! kill -0 "$PID" 2>/dev/null; then
                echo "  → 进程已优雅退出"
                rm -f "$PROJECT_DIR/.app.pid"
                break
            fi
            sleep 1
        done
        # 如果 20 秒还没退出，强制杀掉
        if kill -0 "$PID" 2>/dev/null; then
            echo "  → 进程未响应，强制终止..."
            kill -9 "$PID" 2>/dev/null
            rm -f "$PROJECT_DIR/.app.pid"
        fi
    else
        rm -f "$PROJECT_DIR/.app.pid"
    fi
fi

# 方式2：兜底 — 查找并杀掉占用 8080 端口的所有进程
echo "[2] 检查端口 $PORT 残留进程..."
PORTS=$(cmd.exe //c "netstat -ano | findstr :$PORT | findstr LISTENING" 2>/dev/null | awk '{print $NF}' | sort -u)
if [ -n "$PORTS" ]; then
    for pid in $PORTS; do
        if [ "$pid" -gt 0 ] 2>/dev/null; then
            echo "  → 终止进程 PID: $pid"
            cmd.exe //c "TASKKILL /F /PID $pid" 2>/dev/null
        fi
    done
    echo "  → 端口 $PORT 已释放"
else
    echo "  → 端口 $PORT 无占用"
fi

echo "=============================="
echo "  服务已停止"
echo "=============================="

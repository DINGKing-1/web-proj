#!/bin/bash
# ============================================
#  优品汇 - 启动脚本
# ============================================

cd "$(dirname "$0")"

PORT=8080
PROJECT_DIR="$(pwd)"

echo "=============================="
echo "  优品汇 二手交易平台"
echo "=============================="

# 0. 杀掉占用端口的旧进程
echo "[1/4] 检查端口 $PORT ..."
PID=$(cmd.exe //c "netstat -ano | findstr :$PORT | findstr LISTENING" 2>/dev/null | awk '{print $NF}' | head -1)
if [ -n "$PID" ] && [ "$PID" -gt 0 ] 2>/dev/null; then
    echo "  → 端口 $PORT 被进程 $PID 占用，正在释放..."
    cmd.exe //c "TASKKILL /F /PID $PID" 2>/dev/null
    sleep 2
    echo "  → 端口已释放"
else
    echo "  → 端口空闲"
fi

# 加载环境变量
if [ -f ~/.bashrc ]; then
    source ~/.bashrc 2>/dev/null
fi

# 1. 编译打包
echo "[2/4] 编译打包（跳过测试）..."
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "  ✗ 编译失败！"
    exit 1
fi
echo "  → 编译成功"

# 2. 启动应用（直接用 java -jar，不 fork 子进程）
echo "[3/4] 启动应用..."
JAR_FILE=$(ls target/*.jar | head -1)
java -jar "$JAR_FILE" &
APP_PID=$!

# 3. 等待启动
echo "[4/4] 等待启动..."
sleep 5
# 检查进程是否存活
if kill -0 $APP_PID 2>/dev/null; then
    echo ""
    echo "=============================="
    echo "  启动成功!  PID: $APP_PID"
    echo "  前端地址: http://localhost:$PORT"
    echo "  管理后台: http://localhost:$PORT/admin"
    echo "  默认管理员: admin / admin123"
    echo "=============================="
    echo ""
    echo "  停止服务: ./stop.sh"
    echo "  (按 Ctrl+C 退出查看日志，应用继续在后台运行)"
    echo ""
    # 将 PID 写入文件供 stop.sh 使用
    echo $APP_PID > "$PROJECT_DIR/.app.pid"
else
    echo "  ✗ 启动失败！"
    exit 1
fi

#!/bin/bash
#
# 文件名: lightweight-game-server.sh
# 用途: 轻量级游戏服务器JVM优化配置
# 适用场景: 卡牌游戏、休闲游戏等轻量级游戏
# 内存需求: 2GB+
# CPU需求: 4核+
#

# JVM基础配置
export JAVA_OPTS="
# 内存配置（轻量级）
-Xms1g -Xmx2g
-XX:NewRatio=3
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=256m

# 垃圾回收器配置（G1GC平衡性能和内存）
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=16m
-XX:+G1UseAdaptiveIHOP
-XX:G1MixedGCLiveThresholdPercent=90

# 虚拟线程优化
-Djava.util.concurrent.ForkJoinPool.common.parallelism=8

# 内存优化
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers

# 启动优化
-XX:+TieredCompilation
-XX:TieredStopAtLevel=1

# 基础配置
-Djava.net.preferIPv4Stack=true
-Djava.awt.headless=true
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Shanghai
-Djava.security.egd=file:/dev/./urandom

# 轻量级监控
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/lightweight-game-heapdump.hprof
"

echo "轻量级游戏服务器JVM配置已设置"
echo "内存配置: 2GB堆内存，适合轻量级游戏"
echo "GC配置: G1GC平衡型垃圾回收器，目标<50ms停顿"
echo "优化目标: 快速启动，低内存占用"
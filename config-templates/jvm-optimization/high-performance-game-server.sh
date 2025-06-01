#!/bin/bash
#
# 文件名: high-performance-game-server.sh
# 用途: 高性能游戏服务器JVM优化配置
# 适用场景: MMORPG、MOBA等高并发实时游戏
# 内存需求: 8GB+
# CPU需求: 8核+
#

# JVM基础配置
export JAVA_OPTS="
# 内存配置
-Xms6g -Xmx6g
-XX:NewRatio=2
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# 垃圾回收器配置（ZGC低延迟）
-XX:+UnlockExperimentalVMOptions
-XX:+UseZGC
-XX:MaxGCPauseMillis=1
-XX:ZCollectionInterval=0

# 虚拟线程优化（Java 17+兼容）
-Djava.util.concurrent.ForkJoinPool.common.parallelism=32
-XX:ActiveProcessorCount=32

# 内存优化
-XX:+UseTransparentHugePages
-XX:+AlwaysPreTouch
-XX:+UseNUMA

# 性能调优
-XX:+OptimizeStringConcat
-XX:+UseStringDeduplication
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers

# 网络优化
-Djava.net.preferIPv4Stack=true
-Djava.awt.headless=true

# 编码和时区
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Shanghai

# 安全优化
-Djava.security.egd=file:/dev/./urandom

# 诊断和监控
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/game-server-heapdump.hprof
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xloggc:/tmp/gc.log
"

echo "高性能游戏服务器JVM配置已设置"
echo "内存配置: 6GB堆内存，适合高并发游戏"
echo "GC配置: ZGC低延迟垃圾回收器，目标<1ms停顿"
echo "并发配置: 32个并行线程，支持高并发处理"
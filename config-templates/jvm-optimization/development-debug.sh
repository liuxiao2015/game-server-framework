#!/bin/bash
#
# 文件名: development-debug.sh
# 用途: 开发调试环境JVM配置
# 适用场景: 本地开发、测试调试
# 内存需求: 1GB+
# CPU需求: 2核+
#

# JVM基础配置
export JAVA_OPTS="
# 内存配置（开发环境）
-Xms512m -Xmx1g
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=256m

# 垃圾回收器配置（简单配置）
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100

# 调试配置
-Xdebug
-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/dev-heapdump.hprof

# 开发工具支持
-XX:+UnlockDiagnosticVMOptions
-XX:+LogVMOutput
-XX:+TraceClassLoading

# JMX监控
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.authenticate=false

# 基础配置
-Djava.net.preferIPv4Stack=true
-Djava.awt.headless=true
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Shanghai

# 开发优化
-Dspring.devtools.restart.enabled=true
-Dspring.profiles.active=dev
"

echo "开发调试环境JVM配置已设置"
echo "调试端口: 5005"
echo "JMX端口: 9999"
echo "内存配置: 1GB堆内存，适合开发调试"
echo "特性: 支持热重载，远程调试，JMX监控"
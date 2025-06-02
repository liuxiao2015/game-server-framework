#!/bin/bash
# Simple test script to verify launcher functionality with enabled business modules

echo "=========================================="
echo "Testing Game Server Framework Launcher"
echo "=========================================="

# Set Java 21
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "Java Version: $(java -version 2>&1 | head -1)"
echo ""

# Check enabled modules
echo "Enabled Business Modules:"
grep -v "<!--" pom.xml | grep "<module>business/" | sed 's/.*<module>/- /' | sed 's/<\/module>.*//'
echo ""

# Test launcher startup (timeout after 20 seconds)
echo "Testing launcher startup (20 second timeout)..."
cd launcher
timeout 20s mvn spring-boot:run -q 2>&1 | grep -E "(启动|模块|ERROR|SUCCESS|Failed|游戏服务器框架)" | head -10 || echo "Launcher startup test completed"

echo ""
echo "Test completed!"
/*
 * 文件名: NetworkUtils.java
 * 用途: 网络工具类
 * 实现内容:
 *   - IP地址处理和验证
 *   - 端口检测和可用性检查
 *   - 网络接口信息获取
 *   - 协议工具方法
 *   - 性能优化工具
 * 技术选型:
 *   - Java NIO网络API
 *   - InetAddress工具类
 *   - 网络接口枚举
 * 依赖关系:
 *   - 被NetworkServer使用
 *   - 为其他网络组件提供工具方法
 *   - 支持网络诊断和监控
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.network.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 网络工具类
 * <p>
 * 提供网络相关的常用工具方法，包括IP地址处理、端口检测、
 * 网络接口获取等功能。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public final class NetworkUtils {

    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    // IP地址正则表达式
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::(([0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4})?$"
    );

    // 私有构造函数防止实例化
    private NetworkUtils() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 检查端口是否可用
     *
     * @param port 端口号
     * @return true表示端口可用
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }

        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查端口是否可用（指定绑定地址）
     *
     * @param host 绑定地址
     * @param port 端口号
     * @return true表示端口可用
     */
    public static boolean isPortAvailable(String host, int port) {
        if (port < 1 || port > 65535) {
            return false;
        }

        try {
            InetAddress address = host != null ? InetAddress.getByName(host) : null;
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(address, port));
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 查找可用端口
     *
     * @param startPort 起始端口
     * @param endPort   结束端口
     * @return 第一个可用的端口，如果没有找到返回-1
     */
    public static int findAvailablePort(int startPort, int endPort) {
        for (int port = startPort; port <= endPort; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }

    /**
     * 查找随机可用端口
     *
     * @return 随机可用端口
     */
    public static int findRandomAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            logger.warn("无法获取随机可用端口", e);
            return -1;
        }
    }

    /**
     * 验证IPv4地址
     *
     * @param ip IP地址字符串
     * @return true表示有效的IPv4地址
     */
    public static boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 验证IPv6地址
     *
     * @param ip IP地址字符串
     * @return true表示有效的IPv6地址
     */
    public static boolean isValidIPv6(String ip) {
        return ip != null && IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * 验证IP地址（IPv4或IPv6）
     *
     * @param ip IP地址字符串
     * @return true表示有效的IP地址
     */
    public static boolean isValidIP(String ip) {
        return isValidIPv4(ip) || isValidIPv6(ip);
    }

    /**
     * 检查是否为私有IP地址
     *
     * @param ip IP地址字符串
     * @return true表示私有IP
     */
    public static boolean isPrivateIP(String ip) {
        if (!isValidIPv4(ip)) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 检查是否为本地回环地址
     *
     * @param ip IP地址字符串
     * @return true表示本地回环地址
     */
    public static boolean isLoopbackIP(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 获取本机IP地址
     *
     * @return 本机IP地址列表
     */
    public static List<String> getLocalIPAddresses() {
        List<String> addresses = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            logger.warn("获取本机IP地址失败", e);
        }
        
        return addresses;
    }

    /**
     * 获取首选的本机IP地址
     *
     * @return 首选IP地址，优先返回非私有IP
     */
    public static String getPreferredLocalIP() {
        List<String> addresses = getLocalIPAddresses();
        
        // 优先选择公网IP
        for (String address : addresses) {
            if (!isPrivateIP(address) && isValidIPv4(address)) {
                return address;
            }
        }
        
        // 其次选择私有IP
        for (String address : addresses) {
            if (isPrivateIP(address)) {
                return address;
            }
        }
        
        // 最后返回任意IPv4地址
        for (String address : addresses) {
            if (isValidIPv4(address)) {
                return address;
            }
        }
        
        // 默认返回本地回环
        return "127.0.0.1";
    }

    /**
     * 获取网络接口信息
     *
     * @return 网络接口信息列表
     */
    public static List<NetworkInterfaceInfo> getNetworkInterfaces() {
        List<NetworkInterfaceInfo> interfaces = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                
                List<String> addresses = new ArrayList<>();
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    addresses.add(inetAddresses.nextElement().getHostAddress());
                }
                
                interfaces.add(new NetworkInterfaceInfo(
                    ni.getName(),
                    ni.getDisplayName(),
                    ni.isUp(),
                    ni.isLoopback(),
                    ni.isVirtual(),
                    ni.supportsMulticast(),
                    ni.isPointToPoint(),
                    addresses
                ));
            }
        } catch (SocketException e) {
            logger.warn("获取网络接口信息失败", e);
        }
        
        return interfaces;
    }

    /**
     * 网络接口信息
     */
    public static class NetworkInterfaceInfo {
        private final String name;
        private final String displayName;
        private final boolean up;
        private final boolean loopback;
        private final boolean virtual;
        private final boolean multicast;
        private final boolean pointToPoint;
        private final List<String> addresses;

        public NetworkInterfaceInfo(String name, String displayName, boolean up, boolean loopback,
                                  boolean virtual, boolean multicast, boolean pointToPoint,
                                  List<String> addresses) {
            this.name = name;
            this.displayName = displayName;
            this.up = up;
            this.loopback = loopback;
            this.virtual = virtual;
            this.multicast = multicast;
            this.pointToPoint = pointToPoint;
            this.addresses = new ArrayList<>(addresses);
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public boolean isUp() { return up; }
        public boolean isLoopback() { return loopback; }
        public boolean isVirtual() { return virtual; }
        public boolean isMulticast() { return multicast; }
        public boolean isPointToPoint() { return pointToPoint; }
        public List<String> getAddresses() { return new ArrayList<>(addresses); }

        @Override
        public String toString() {
            return String.format("NetworkInterface{name='%s', displayName='%s', up=%s, " +
                               "loopback=%s, virtual=%s, multicast=%s, pointToPoint=%s, addresses=%s}",
                               name, displayName, up, loopback, virtual, multicast, pointToPoint, addresses);
        }
    }

    /**
     * 检查主机连通性
     *
     * @param host    主机地址
     * @param timeout 超时时间（毫秒）
     * @return true表示可达
     */
    public static boolean isHostReachable(String host, int timeout) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isReachable(timeout);
        } catch (IOException e) {
            logger.debug("主机不可达: {}", host, e);
            return false;
        }
    }

    /**
     * 检查TCP端口连通性
     *
     * @param host    主机地址
     * @param port    端口号
     * @param timeout 超时时间（毫秒）
     * @return true表示端口可连接
     */
    public static boolean isTcpPortReachable(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (IOException e) {
            logger.debug("TCP端口不可达: {}:{}", host, port, e);
            return false;
        }
    }

    /**
     * 解析主机名到IP地址
     *
     * @param hostname 主机名
     * @return IP地址列表
     */
    public static List<String> resolveHostname(String hostname) {
        List<String> addresses = new ArrayList<>();
        
        try {
            InetAddress[] resolved = InetAddress.getAllByName(hostname);
            for (InetAddress address : resolved) {
                addresses.add(address.getHostAddress());
            }
        } catch (UnknownHostException e) {
            logger.debug("解析主机名失败: {}", hostname, e);
        }
        
        return addresses;
    }

    /**
     * 获取IP地址的字节表示
     *
     * @param ip IP地址字符串
     * @return 字节数组，失败时返回null
     */
    public static byte[] getIPBytes(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.getAddress();
        } catch (UnknownHostException e) {
            logger.debug("无法获取IP字节表示: {}", ip, e);
            return null;
        }
    }

    /**
     * 字节数组转IP地址
     *
     * @param bytes IP地址字节数组
     * @return IP地址字符串
     */
    public static String bytesToIP(byte[] bytes) {
        try {
            InetAddress address = InetAddress.getByAddress(bytes);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            logger.debug("无法从字节数组转换IP地址", e);
            return null;
        }
    }

    /**
     * 计算网络延迟（简单实现）
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return 延迟时间（毫秒），失败时返回-1
     */
    public static long measureLatency(String host, int port) {
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return System.currentTimeMillis() - startTime;
        } catch (IOException e) {
            logger.debug("测量网络延迟失败: {}:{}", host, port, e);
            return -1;
        }
    }

    /**
     * 格式化IP地址（统一IPv6格式）
     *
     * @param ip 原始IP地址
     * @return 格式化后的IP地址
     */
    public static String formatIP(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return ip; // 原样返回
        }
    }

    /**
     * 检查IP是否在指定网段内
     *
     * @param ip     要检查的IP
     * @param subnet 网段（如：192.168.1.0/24）
     * @return true表示在网段内
     */
    public static boolean isIPInSubnet(String ip, String subnet) {
        try {
            String[] parts = subnet.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            InetAddress targetAddr = InetAddress.getByName(ip);
            InetAddress subnetAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] targetBytes = targetAddr.getAddress();
            byte[] subnetBytes = subnetAddr.getAddress();
            
            if (targetBytes.length != subnetBytes.length) {
                return false;
            }
            
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;
            
            // 检查完整字节
            for (int i = 0; i < bytesToCheck; i++) {
                if (targetBytes[i] != subnetBytes[i]) {
                    return false;
                }
            }
            
            // 检查剩余位
            if (bitsToCheck > 0 && bytesToCheck < targetBytes.length) {
                int mask = 0xFF << (8 - bitsToCheck);
                return (targetBytes[bytesToCheck] & mask) == (subnetBytes[bytesToCheck] & mask);
            }
            
            return true;
        } catch (Exception e) {
            logger.debug("检查IP网段失败: {} in {}", ip, subnet, e);
            return false;
        }
    }

    /**
     * 获取系统网络统计信息
     *
     * @return 网络统计信息
     */
    public static Map<String, Object> getNetworkStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取网络接口数量
        List<NetworkInterfaceInfo> interfaces = getNetworkInterfaces();
        stats.put("interfaceCount", interfaces.size());
        stats.put("activeInterfaces", interfaces.stream().mapToLong(ni -> ni.isUp() ? 1 : 0).sum());
        
        // 获取IP地址数量
        List<String> localIPs = getLocalIPAddresses();
        stats.put("localIPCount", localIPs.size());
        stats.put("preferredIP", getPreferredLocalIP());
        
        return stats;
    }
}
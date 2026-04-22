package com.taoyuanx.common.audit.log.runtime.util;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

/**
 * IP地址工具类
 * 获取本机IP地址，兼容Linux、Windows、macOS等操作系统
 */
@Slf4j
public class IpUtil {


    /**
     * 缓存的本机IP地址
     */
    private static volatile String localIp = null;


    /**
     * 读取本地的IP地址（使用默认排除列表）
     * 兼容Linux、Windows、macOS
     *
     * @return 本机IP地址
     * @throws SocketException 如果无法获取IP地址
     */
    public static String getLocalIp() throws SocketException {
        if (localIp != null) {
            return localIp;
        }
        localIp = getLocalIpJava(null);
        return localIp;
    }

    /**
     * 读取本地的IP地址
     * 兼容Linux、Windows、macOS
     *
     * @param exclusionIps 需要排除的IP地址集合
     * @return 本机IP地址
     * @throws SocketException 如果无法获取IP地址
     */
    public static String getLocalIp(Set<String> exclusionIps) throws SocketException {
        return getLocalIpJava(exclusionIps);
    }


    /**
     * 通过Java API获取本机IP地址
     */
    private static String getLocalIpJava(Set<String> exclusionIps) throws SocketException {
        Set<String> exclusionSet = exclusionIps != null ? exclusionIps : Collections.<String>emptySet();

        List<NetworkInterface> nis = new ArrayList<NetworkInterface>();
        Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();

        if (netInterfaces == null) {
            throw new SocketException("无法获取网络接口，请检查网络配置");
        }

        while (netInterfaces.hasMoreElements()) {
            nis.add(netInterfaces.nextElement());
        }

        // 按优先级排序网卡
        Collections.sort(nis, new NetworkInterfaceComparator());

        // 收集诊断信息
        List<String> availableInterfaces = new ArrayList<String>();

        for (NetworkInterface ni : nis) {
            String niName = ni.getName();

            // 检查网卡是否激活
            if (!ni.isUp()) {
                log.info("网卡 {0} 未激活，跳过", niName);
                continue;
            }

            // 遍历该网卡的所有IP地址
            Enumeration<InetAddress> ips = ni.getInetAddresses();
            while (ips.hasMoreElements()) {
                InetAddress ip = ips.nextElement();
                String hostAddress = ip.getHostAddress();

                // 收集可用IP信息
                availableInterfaces.add(String.format("%s: %s", niName, hostAddress));

                // 过滤：非回环地址 + IPv4 + 不在排除列表
                if (!ip.isLoopbackAddress()
                        && ip instanceof Inet4Address
                        && !exclusionSet.contains(hostAddress)) {
                    log.info("选择网卡 {0} 的IP地址: {1}", new Object[]{niName, hostAddress});
                    return ip.getHostAddress();
                }
            }
        }

        // 无法获取IP时，提供详细的诊断信息
        String diagnosticInfo = String.format(
                "无法获取有效的IP地址。可用的网络接口: %s。请检查网络配置或手动配置IP地址。",
                availableInterfaces.isEmpty() ? "无" : String.join(", ", availableInterfaces)
        );
        log.info(diagnosticInfo);
        throw new SocketException(diagnosticInfo);
    }

    /**
     * 网卡接口排序比较器
     * 按照优先级对网卡进行排序：物理网卡 > 虚拟网卡
     */
    private static class NetworkInterfaceComparator implements Comparator<NetworkInterface> {

        /**
         * 网卡优先级配置
         * 数值越小优先级越高
         */
        private static final List<String> PRIORITY_PREFIXES = new ArrayList<String>() {{
            // 物理网卡优先级最高
            add("eth");   // Linux以太网卡 (eth0, eth1)
            add("en");    // macOS/新版Linux以太网卡 (en0, en1, ens33)
            add("wlan");  // Windows无线网卡 (wlan0, wlan1)
            add("ens");   // Linux以太网卡 (systemd命名: ens33, ens192)
            add("em");    // Linux以太网卡 (Dell命名: em1, em2)
            add("enp");   // Linux以太网卡 (可预测命名: enp0s3)

            // 云服务器网卡
            add("ens");   // 阿里云、AWS等云服务器
            // 虚拟网卡优先级较低（通过排序位置隐式处理）
        }};

        /**
         * 虚拟网卡前缀，优先级最低
         */
        private static final List<String> VIRTUAL_PREFIXES = new ArrayList<String>() {{
            add("docker");  // Docker网桥 (docker0)
            add("br-");     // Docker自定义网桥 (br-xxx)
            add("veth");    // 虚拟以太网接口 (vethxxx)
            add("virbr");   // libvirt虚拟网桥 (virbr0)
            add("vnic");    // 虚拟网卡
            add("vmnet");   // VMware虚拟网卡
            add("tun");     // VPN隧道
            add("tap");     // 虚拟网络设备
            add("lo");      // 回环接口
        }};

        /**
         * 计算网卡的优先级等级
         *
         * @param name 网卡名称
         * @return 优先级等级，数值越小优先级越高
         */
        private int getLevel(String name) {
            if (name == null) {
                return Integer.MAX_VALUE;
            }

            String lowerName = name.toLowerCase();

            // 虚拟网卡优先级最低
            for (String virtualPre : VIRTUAL_PREFIXES) {
                if (lowerName.startsWith(virtualPre.toLowerCase())) {
                    return 1000 + VIRTUAL_PREFIXES.indexOf(virtualPre);
                }
            }

            // 物理网卡按配置顺序
            for (int i = 0; i < PRIORITY_PREFIXES.size(); i++) {
                if (lowerName.startsWith(PRIORITY_PREFIXES.get(i).toLowerCase())) {
                    return i + 1;
                }
            }

            // 未识别的网卡，优先级中等
            return 500;
        }

        /**
         * 提取网卡编号
         */
        private int getIndex(String name, int level) {
            if (level > 100) {
                // 虚拟网卡或未知网卡
                return name.hashCode();
            }

            // 物理网卡，提取编号
            int prefixIndex = level - 1;
            if (prefixIndex >= 0 && prefixIndex < PRIORITY_PREFIXES.size()) {
                String prefix = PRIORITY_PREFIXES.get(prefixIndex);
                String num = name.replaceFirst("(?i)" + prefix, "");
                try {
                    return Integer.parseInt(num.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    return num.hashCode();
                }
            }

            return name.hashCode();
        }

        @Override
        public int compare(NetworkInterface o1, NetworkInterface o2) {
            String name1 = o1.getName();
            String name2 = o2.getName();

            // null值处理
            if (name1 == null && name2 == null) {
                return 0;
            }
            if (name1 == null) {
                return 1;
            }
            if (name2 == null) {
                return -1;
            }

            // 先比较优先级
            int level1 = getLevel(name1);
            int level2 = getLevel(name2);
            if (level1 != level2) {
                return level1 - level2;  // 升序，优先级高的排前面
            }

            // 同一优先级，按编号排序
            if (level1 <= PRIORITY_PREFIXES.size()) {
                return getIndex(name1, level1) - getIndex(name2, level2);
            }

            // 其他情况按字母排序
            return name1.compareTo(name2);
        }
    }



}
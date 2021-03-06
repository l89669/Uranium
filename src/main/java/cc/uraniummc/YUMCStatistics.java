/*
 * Copyright 2011-2015 喵♂呜. All rights reserved.
 */
package cc.uraniummc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;

import cpw.mods.fml.common.FMLCommonHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


/**
 * Yum数据中心 数据统计类
 *
 * @since 2015年12月14日 下午1:36:42
 * @author 喵♂呜
 */
public class YUMCStatistics {
    /**
     * 统计系统版本
     */
    private final static int REVISION = 10;

    /**
     * 统计插件基础配置文件
     */
    private final static File configfile = new File("plugins" + File.separatorChar + "PluginHelper", "config.yml");

    /**
     * UTF-8编码
     */
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * 统计插件基础配置文件
     */
    private YamlConfiguration config;

    /**
     * 调试模式
     */
    private final boolean debug;

    /**
     * 唯一服务器编码
     */
    private final String guid;

    /**
     * 线程任务
     */
    private volatile TimerTask task = null;

    /**
     * 插件使用数据统计
     *
     * @throws IOException
     *             IO异常
     */
    public YUMCStatistics() {
        try {
            if (!configfile.exists()) {
                configfile.getParentFile().mkdirs();
                configfile.createNewFile();
            }
            config = YamlConfiguration.loadConfiguration(configfile);
            initFile(config);
        } catch (final IOException e) {
        }
        this.guid = config.getString("guid");
        this.debug = config.getBoolean("debug", false);
        start();
    }

    /**
     * 向指定 URL 发送POST方法的请求
     *
     * @param url
     *            发送请求的 URL
     * @param param
     *            请求参数
     * @return 所代表远程资源的响应结果
     */
    public static String postData(final String url, final String param) {
        PrintWriter out = null;
        String result = "";
        try {
            final URL realUrl = new URL(url);
            // 打开和URL之间的连接
            final URLConnection conn = realUrl.openConnection();
            // 设置通用的请求属性
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36");
            // 设置超时时间 10秒
            conn.setReadTimeout(10000);
            // 发送POST请求必须设置如下两行
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // 获取URLConnection对象对应的输出流
            out = new PrintWriter(conn.getOutputStream());
            // 发送请求参数
            out.write(param);
            // flush输出流的缓冲
            out.flush();
            String response = "";
            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8));
            while ((response = reader.readLine()) != null) {
                result += response;
            }
            reader.close();
        } catch (final Exception e) {
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return result;
    }

    /**
     * 初始化配置文件
     *
     * @param config
     *            配置文件
     * @throws IOException
     */
    private static void initFile(final YamlConfiguration config) throws IOException {
        if (config.getString("guid") == null) {
            config.options().header("YUMC数据中心 http://www.yumc.pw 收集的数据仅用于统计插件使用情况").copyDefaults(true);
            config.set("guid", UUID.randomUUID().toString());
            config.set("debug", false);
            config.save(configfile);
        }
        if (!config.contains("YumAccount")) {
            config.set("YumAccount.username", "Username Not Set");
            config.set("YumAccount.password", "Password NotSet");
            config.save(configfile);
        }
        if (!config.contains("TellrawManualHandle")) {
            config.set("TellrawManualHandle", false);
            config.save(configfile);
        }
    }

    /**
     * 简化输出
     *
     * @param msg
     *            输出对象
     */
    public void print(final String msg) {
        if (debug) {
            System.out.println("[YUMCStatistics] " + msg);
        }
    }

    /**
     * 开启数据统计 这将会在异步执行
     *
     * @return 是否运行成功.
     */
    public boolean start() {
        if (task != null) {
            return true;
        }
        // 开启发送数据线程
        task=(new TimerTask() {
            @Override
            public void run() {
                try {
                    postPlugin();
                } catch (final Throwable e) {
                    if (debug) {
                        e.printStackTrace();
                    }
                }
            }
        });
        new Timer().scheduleAtFixedRate(task,1000*60*5,1000*60*30);
        return true;
    }
    /**
     * 发送服务器数据到统计网页
     */
    private void postPlugin() throws IOException {
        // 服务器数据获取
        final String pluginname = Uranium.name;
        final String tmposarch = System.getProperty("os.arch");

        final Map<String, Object> data = new HashMap();
        data.put("guid", guid);
        data.put("server_version", Bukkit.getVersion());
        data.put("server_port", Bukkit.getServer().getPort());
        data.put("server_tps", FMLCommonHandler.instance().getMinecraftServerInstance().recentTps[1]);
        data.put("plugin_version", Uranium.getCurrentVersion());
        data.put("players_online", Bukkit.getServer().getOnlinePlayers().size());
        data.put("os_name", System.getProperty("os.name"));
        data.put("os_arch", tmposarch.equalsIgnoreCase("amd64") ? "x86_64" : tmposarch);
        data.put("os_version", System.getProperty("os.version"));
        data.put("os_usemem", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
        data.put("os_cores", Runtime.getRuntime().availableProcessors());
        data.put("auth_mode", Bukkit.getServer().getOnlineMode() ? 1 : 0);
        data.put("java_version", System.getProperty("java.version"));

        final String jsondata = "Info=" + JSONValue.toJSONString(data);

        final String url = String.format("http://api.yumc.pw/I/P/S/V/%s/P/%s", REVISION, URLEncoder.encode(pluginname, "UTF-8"));
        print("Plugin: " + pluginname + " Send Data To CityCraft Data Center");
        print("Address: " + url);
        print("Data: " + jsondata);
        // 发送数据
        final JSONObject result = (JSONObject) JSONValue.parse(postData(url, jsondata));
        print("Plugin: " + pluginname + " Recover Data From CityCraft Data Center: " + result.get("info"));
    }
}
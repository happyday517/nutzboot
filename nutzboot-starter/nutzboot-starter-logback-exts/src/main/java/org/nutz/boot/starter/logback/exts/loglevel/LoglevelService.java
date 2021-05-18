package org.nutz.boot.starter.logback.exts.loglevel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.nutz.integration.jedis.JedisAgent;
import org.nutz.integration.jedis.pubsub.PubSub;
import org.nutz.integration.jedis.pubsub.PubSubService;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.json.JsonFormat;
import org.nutz.lang.Streams;
import org.nutz.lang.Strings;
import org.nutz.lang.util.NutMap;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import java.util.ArrayList;
import java.util.List;

@IocBean(create = "init")
public class LoglevelService implements PubSub {
    private static final Log log = Logs.get();
    @Inject
    protected LoglevelProperty loglevelProperty;
    @Inject
    protected JedisAgent jedisAgent;
    @Inject
    protected PubSubService pubSubService;
    @Inject
    protected LoglevelHeartbeatThread loglevelHeartbeatThread;

    /**
     * 初始化数据到redis并订阅主题
     */
    public void init() {
        pubSubService.reg(loglevelProperty.getREDIS_KEY_PREFIX() + "pubsub", this);
        saveToRedis();
        doHeartbeat();
    }

    public void saveToRedis() {
        long vmFree = 0;
        long vmUse = 0;
        long vmTotal = 0;
        long vmMax = 0;
        int byteToMb = 1024 * 1024;
        Runtime rt = Runtime.getRuntime();
        vmTotal = rt.totalMemory() / byteToMb;
        vmFree = rt.freeMemory() / byteToMb;
        vmMax = rt.maxMemory() / byteToMb;
        vmUse = vmTotal - vmFree;
        loglevelProperty.setVmTotal(vmTotal);
        loglevelProperty.setVmFree(vmFree);
        loglevelProperty.setVmMax(vmMax);
        loglevelProperty.setVmUse(vmUse);
        loglevelProperty.setLoglevel(getLevel());
        //log.debug("LoglevelService saveToRedis::"+Json.toJson(loglevelProperty));
        Jedis jedis = null;
        try {
            jedis = jedisAgent.jedis();
            jedis.setex(loglevelProperty.getREDIS_KEY_PREFIX() + "list:" + loglevelProperty.getName() + ":" + loglevelProperty.getProcessId(), loglevelProperty.getKeepalive(), Json.toJson(loglevelProperty, JsonFormat.compact()));
        } finally {
            Streams.safeClose(jedis);
        }
    }

    /**
     * 启动心跳线程
     */
    private void doHeartbeat() {
        loglevelHeartbeatThread.start();
    }

    /**
     * 发送消息
     *
     * @param loglevelCommand
     */
    public void changeLoglevel(LoglevelCommand loglevelCommand) {
        pubSubService.fire(loglevelProperty.getREDIS_KEY_PREFIX() + "pubsub", Json.toJson(loglevelCommand, JsonFormat.compact()));
    }

    /**
     * 消息处理
     *
     * @param channel
     * @param message
     */
    @Override
    public void onMessage(String channel, String message) {
        LoglevelCommand loglevelCommand = Json.fromJson(LoglevelCommand.class, message);
        //通过实例名称更改日志等级 或 通过进程ID更改日志等级
        if (("name".equals(loglevelCommand.getAction())
                && Strings.sNull(loglevelCommand.getName()).equals(loglevelProperty.getName())) ||
                ("processId".equals(loglevelCommand.getAction()) && Strings.sNull(loglevelCommand.getProcessId()).equals(loglevelProperty.getProcessId()))) {
            //更改之前
            System.out.println("logback loglevel change start.");
            testLevel();
            if (Strings.isNotBlank(loglevelCommand.getLevel())) {
                setLevel(loglevelCommand.getLevel());
                saveToRedis();
            }
            System.out.println("----------------------------");
            //更改之后
            testLevel();
            System.out.println("logback loglevel change end.");
        }
    }

    /**
     * 设置当前进程日志等级
     *
     * @param level
     * @return
     */
    public boolean setLevel(String level) {
        boolean isSucceed = true;
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("root").setLevel(Level.valueOf(level));
        } catch (Exception e) {
            e.printStackTrace();
            isSucceed = false;
        }
        return isSucceed;
    }

    /**
     * 获取当前进程日志等级
     *
     * @return
     */
    public String getLevel() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            return loggerContext.getLogger("root").getLevel().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 测试日志等级
     */
    private void testLevel() {
        log.info("info -- hello");
        log.warn("warn -- hello");
        log.error("error -- hello");
        log.debug("debug -- hello");
    }

    /**
     * 获取客户端列表
     *
     * @return
     */
    public NutMap getData() {
        NutMap map = NutMap.NEW();
        if (jedisAgent.isClusterMode()) {
            JedisCluster jedisCluster = jedisAgent.getJedisClusterWrapper().getJedisCluster();
            List<String> keys = new ArrayList<>();
            for (JedisPool pool : jedisCluster.getClusterNodes().values()) {
                try (Jedis jedis = pool.getResource()) {
                    ScanParams match = new ScanParams().match(loglevelProperty.getREDIS_KEY_PREFIX() + "list:*");
                    ScanResult<String> scan = null;
                    do {
                        scan = jedis.scan(scan == null ? ScanParams.SCAN_POINTER_START : scan.getStringCursor(), match);
                        keys.addAll(scan.getResult());
                    } while (!scan.isCompleteIteration());
                }
            }
            Jedis jedis = null;
            try {
                jedis = jedisAgent.jedis();
                for (String key : keys) {
                    String[] tmp = key.split(":");
                    String name = tmp[3];
                    LoglevelProperty loglevelProperty = Json.fromJson(LoglevelProperty.class, jedis.get(key));
                    map.addv2(name, loglevelProperty);
                }
            } finally {
                Streams.safeClose(jedis);
            }
        } else {
            Jedis jedis = null;
            try {
                jedis = jedisAgent.jedis();
                ScanParams match = new ScanParams().match(loglevelProperty.getREDIS_KEY_PREFIX() + "list:*");
                ScanResult<String> scan = null;
                do {
                    scan = jedis.scan(scan == null ? ScanParams.SCAN_POINTER_START : scan.getStringCursor(), match);
                    for (String key : scan.getResult()) {
                        String[] keys = key.split(":");
                        String name = keys[3];
                        LoglevelProperty loglevelProperty = Json.fromJson(LoglevelProperty.class, jedis.get(key));
                        map.addv2(name, loglevelProperty);
                    }
                    // 已经迭代结束了
                } while (!scan.isCompleteIteration());
            } finally {
                Streams.safeClose(jedis);
            }
        }
        return map;
    }

}

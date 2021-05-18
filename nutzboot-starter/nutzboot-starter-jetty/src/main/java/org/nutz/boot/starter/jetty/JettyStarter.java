package org.nutz.boot.starter.jetty;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.Deflater;

import javax.servlet.SessionCookieConfig;
import javax.sql.DataSource;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.session.DatabaseAdaptor;
import org.eclipse.jetty.server.session.DefaultSessionCache;
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.eclipse.jetty.server.session.FileSessionDataStoreFactory;
import org.eclipse.jetty.server.session.HouseKeeper;
import org.eclipse.jetty.server.session.JDBCSessionDataStoreFactory;
import org.eclipse.jetty.server.session.SessionCache;
import org.eclipse.jetty.server.session.SessionDataStore;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.nutz.boot.annotation.PropDoc;
import org.nutz.boot.starter.MonitorObject;
import org.nutz.boot.starter.ServerFace;
import org.nutz.boot.starter.servlet3.AbstractServletContainerStarter;
import org.nutz.boot.starter.servlet3.NbServletContextListener;
import org.nutz.castor.Castors;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.lang.Lang;
import org.nutz.lang.Nums;
import org.nutz.lang.Strings;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.resource.Scans;

@IocBean
public class JettyStarter extends AbstractServletContainerStarter implements ServerFace, MonitorObject {

    private static final Log log = Logs.get();

    protected static final String PRE = "jetty.";

    @PropDoc(value = "监听的端口", defaultValue = "8080", type = "int")
    public static final String PROP_PORT = PRE + "port";

    @PropDoc(value = "监听的ip地址", defaultValue = "0.0.0.0")
    public static final String PROP_HOST = PRE + "host";

    @PropDoc(value = "线程池idleTimeout，单位毫秒", defaultValue = "60000", type = "int")
    public static final String PROP_THREADPOOL_TIMEOUT = PRE + "threadpool.idleTimeout";

    @PropDoc(value = "线程池最小线程数minThreads", defaultValue = "200", type = "int")
    public static final String PROP_THREADPOOL_MINTHREADS = PRE + "threadpool.minThreads";

    @PropDoc(value = "线程池最大线程数maxThreads", defaultValue = "500", type = "int")
    public static final String PROP_THREADPOOL_MAXTHREADS = PRE + "threadpool.maxThreads";

    @PropDoc(value = "空闲时间,单位毫秒", defaultValue = "300000", type = "int")
    public static final String PROP_IDLE_TIMEOUT = PRE + "http.idleTimeout";

    @PropDoc(value = "上下文路径", defaultValue = "/")
    public static final String PROP_CONTEXT_PATH = PRE + "contextPath";

    @PropDoc(value = "表单最大尺寸", defaultValue = "1gb", type = "int")
    public static final String PROP_MAX_FORM_CONTENT_SIZE = PRE + "maxFormContentSize";

    @PropDoc(value = "表单最大key数量", defaultValue = "1000", type = "int")
    public static final String PROP_MAX_FORM_KEYS = PRE + "maxFormKeys";

    @PropDoc(value = "Session空闲时间,单位分钟", defaultValue = "30", type = "int")
    public static final String PROP_SESSION_TIMEOUT = "web.session.timeout";

    @PropDoc(value = "静态文件所在的本地路径")
    public static final String PROP_STATIC_PATH_LOCAL = PRE + "staticPathLocal";
    @PropDoc(value = "额外的静态文件路径")
    public static final String PROP_STATIC_PATH = PRE + "staticPath";

    // ------------------ HttpConfiguration
    @PropDoc(value = "安全协议,例如https")
    public static final String PROP_HTTP_CONFIG_SECURESCHEME = PRE + "httpConfig.secureScheme";
    @PropDoc(value = "安全协议的端口,例如8443")
    public static final String PROP_HTTP_CONFIG_SECUREPORT = PRE + "httpConfig.securePort";
    @PropDoc(value = "输出缓冲区大小", defaultValue = "32768")
    public static final String PROP_HTTP_CONFIG_OUTPUTBUFFERSIZE = PRE + "httpConfig.outputBufferSize";
    @PropDoc(value = "输出聚合大小", defaultValue = "8192")
    public static final String PROP_HTTP_CONFIG_OUTPUTAGGREGATIONSIZE = PRE + "httpConfig.outputAggregationSize";
    @PropDoc(value = "请求的头部最大值", defaultValue = "8192")
    public static final String PROP_HTTP_CONFIG_REQUESTHEADERSIZE = PRE + "httpConfig.requestHeaderSize";
    @PropDoc(value = "响应的头部最大值", defaultValue = "8192")
    public static final String PROP_HTTP_CONFIG_RESPONSEHEADERSIZE = PRE + "httpConfig.responseHeaderSize";
    @PropDoc(value = "是否发送jetty版本号", defaultValue = "true")
    public static final String PROP_HTTP_CONFIG_SENDSERVERVERSION = PRE + "httpConfig.sendServerVersion";
    @PropDoc(value = "是否发送日期信息", defaultValue = "true")
    public static final String PROP_HTTP_CONFIG_SENDDATEHEADER = PRE + "httpConfig.sendDateHeader";
    @PropDoc(value = "头部缓冲区大小", defaultValue = "8192")
    public static final String PROP_HTTP_CONFIG_HEADERCACHESIZE = PRE + "httpConfig.headerCacheSize";
    @PropDoc(value = "最大错误重定向次数", defaultValue = "10")
    public static final String PROP_HTTP_CONFIG_MAXERRORDISPATCHES = PRE + "httpConfig.maxErrorDispatches";
    @PropDoc(value = "阻塞超时", defaultValue = "-1")
    public static final String PROP_HTTP_CONFIG_BLOCKINGTIMEOUT = PRE + "httpConfig.blockingTimeout";
    @PropDoc(value = "是否启用持久化连接", defaultValue = "true")
    public static final String PROP_HTTP_CONFIG_PERSISTENTCONNECTIONSENABLED = PRE + "httpConfig.persistentConnectionsEnabled";
    @PropDoc(value = "自定义404页面,同理,其他状态码也是支持的")
    public static final String PROP_PAGE_404 = PRE + "page.404";
    @PropDoc(value = "自定义java.lang.Throwable页面,同理,其他异常也支持")
    public static final String PROP_PAGE_THROWABLE = PRE + "page.java.lang.Throwable";

    // Gzip
    @PropDoc(value = "是否启用gzip", defaultValue = "false")
    public static final String PROP_GZIP_ENABLE = PRE + "gzip.enable";

    @PropDoc(value = "gzip压缩级别", defaultValue = "-1")
    public static final String PROP_GZIP_LEVEL = PRE + "gzip.level";

    @PropDoc(value = "gzip压缩最小触发大小", defaultValue = "512")
    public static final String PROP_GZIP_MIN_CONTENT_SIZE = PRE + "gzip.minContentSize";

    @PropDoc(value = "WelcomeFile列表", defaultValue = "index.html,index.htm,index.do")
    public static final String PROP_WELCOME_FILES = PRE + "welcome_files";

    // HTTPS相关
    @PropDoc(value = "Https端口号")
    public static final String PROP_HTTPS_PORT = PRE + "https.port";
    @PropDoc(value = "Https的KeyStore路径")
    public static final String PROP_HTTPS_KEYSTORE_PATH = PRE + "https.keystore.path";
    @PropDoc(value = "Https的KeyStore的密码")
    public static final String PROP_HTTPS_KEYSTORE_PASSWORD = PRE + "https.keystore.password";

    // Session持久化相关
    @PropDoc(value = "是否启用session持久化", defaultValue = "false")
    public static final String PROP_SESSION_STORE_ENABLE = PRE + "session.store.enable";
    @PropDoc(value = "session持久化类型", defaultValue = "jdbc", possible = {"jdbc", "file", "ioc", "redis"})
    public static final String PROP_SESSION_STORE_TYPE = PRE + "session.store.type";
    @PropDoc(value = "session持久化,jdbc所用数据库源的ioc名称", defaultValue = "dataSource")
    public static final String PROP_SESSION_JDBC_DATASOURCE_IOCNAME = PRE + "session.jdbc.datasource.iocname";
    @PropDoc(value = "session持久化,file所用的目录", defaultValue = "./session")
    public static final String PROP_SESSION_FILE_STOREDIR = PRE + "session.file.storeDir";
    @PropDoc(value = "session持久化,SessionDataStore对应的ioc名称", defaultValue = "jettySessionDataStore")
    public static final String PROP_SESSION_IOC_DATASTORE = PRE + "session.ioc.datastore";
    
    @PropDoc(value = "扫描session过期的间隔", defaultValue = "600")
    public static final String PROP_SESSION_SCAVENGE_TNTERVAL = "jetty.sessionScavengeInterval.seconds";
    
    // Cookie相关
    @PropDoc(value = "cookie是否设置HttpOnly", defaultValue = "false")
    public static final String PROP_SESSION_COOKIE_HTTPONLY = PRE + "session.cookie.httponly";

    @PropDoc(value = "cookie是否设置Secure" ,defaultValue = "false")
    public static final String PROP_SESSION_COOKIE_SECURE = PRE + "session.cookie.secure";

    @PropDoc(value = "设置cookie的name")
    public static final String PROP_SESSION_COOKIE_NAME = PRE + "session.cookie.name";
    
    @PropDoc(value = "设置cookie的domain")
    public static final String PROP_SESSION_COOKIE_DOMAIN = PRE + "session.cookie.domain";
    
    @PropDoc(value = "设置cookie的path")
    public static final String PROP_SESSION_COOKIE_PATH = PRE + "session.cookie.path";
    
    @PropDoc(value = "设置jetty的临时目录", defaultValue = "./temp")
    public static final String PROP_TEMP_DIR = PRE + "tempdir";

    @PropDoc(value = "配置多个端口监听", defaultValue = "")
    public static final String PROP_EXT_PORTS = PRE + "extports";

    protected Server server;
    protected WebAppContext wac;
    protected ServerConnector connector;

    public void start() throws Exception {
        server.start();
        if (log.isDebugEnabled())
            log.debug("Jetty monitor props:\r\n" + getMonitorForPrint());
    }

    public void stop() throws Exception {
        server.stop();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    @IocBean(name = "jettyServer")
    public Server getJettyServer() {
        return server;
    }

    public void init() throws Exception {

        // 创建基础服务器
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setIdleTimeout(getThreadPoolIdleTimeout());
        threadPool.setMinThreads(getMinThreads());
        threadPool.setMaxThreads(getMaxThreads());
        server = new Server(threadPool);
        // HTTP端口设置
        if (conf.getBoolean("jetty.http.enable", true)) {
            HttpConfiguration httpConfig = conf.make(HttpConfiguration.class, "jetty.httpConfig.");
            HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);
            connector = new ServerConnector(server, httpFactory);
            connector.setHost(getHost());
            connector.setPort(getPort());
            connector.setIdleTimeout(getIdleTimeout());
            server.addConnector(connector);
            // 配置多端口监听
            if(conf.has(PROP_EXT_PORTS)) {
                int[] ports = Nums.splitInt(conf.get(PROP_EXT_PORTS));
                for (int port : ports) {
                    log.debugf("jetty http add port: %s", port);
                    ServerConnector extConnector = new ServerConnector(server, httpFactory);
                    extConnector.setHost(getHost());
                    extConnector.setPort(port);
                    extConnector.setIdleTimeout(getIdleTimeout());
                    server.addConnector(extConnector);
                }
            }
            updateMonitorValue("http.enable", true);
            updateMonitorValue("http.port", connector.getPort());
            updateMonitorValue("http.host", connector.getHost());
            updateMonitorValue("http.idleTimeout", connector.getIdleTimeout());
        }
        else {
        	log.info("jetty http is disable");
        	updateMonitorValue("http.enable", false);
        }

        // 看看Https设置
        int httpsPort = conf.getInt(PROP_HTTPS_PORT);
        if (httpsPort > 0) {
            log.info("found https port " + httpsPort);
            HttpConfiguration https_config = conf.make(HttpConfiguration.class, "jetty.httpsConfig.");
            https_config.setSecureScheme("https");

            SslContextFactory sslContextFactory = new SslContextFactory.Server();
            String ksPath = conf.check(PROP_HTTPS_KEYSTORE_PATH);
            try {
				sslContextFactory.setKeyStorePath(ksPath);
				Resource ks = sslContextFactory.getKeyStoreResource();
				if (ks instanceof PathResource && !((PathResource)ks).exists()) {
					throw new IllegalArgumentException("keystore not exist: " + ksPath);
				}
			} catch (IllegalArgumentException e) {
				URL url = appContext.getClassLoader().getResource(ksPath);
				if (url != null)
					sslContextFactory.setKeyStoreResource(Resource.newResource(url));
				else
					throw e;
			}
            // 私钥
            sslContextFactory.setKeyStorePassword(conf.get(PROP_HTTPS_KEYSTORE_PASSWORD));
            // 公钥
            sslContextFactory.setKeyManagerPassword(conf.get("jetty.https.keymanager.password"));

            ServerConnector httpsConnector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https_config));
            // 设置访问端口
            httpsConnector.setPort(httpsPort);
            httpsConnector.setHost(getHost());
            httpsConnector.setIdleTimeout(getIdleTimeout());
            server.addConnector(httpsConnector);

            updateMonitorValue("https.enable", true);
            updateMonitorValue("https.port", httpsConnector.getPort());
            updateMonitorValue("https.host", httpsConnector.getHost());
            updateMonitorValue("https.idleTimeout", httpsConnector.getIdleTimeout());
        } else {
            updateMonitorValue("https.enable", false);
        }

        // 设置应用上下文
        wac = new WebAppContext();
        wac.setContextPath(getContextPath());

        // wac.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
        // ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$");
        // wac.setAttribute("WebAppContext", value);
        // wac.setExtractWAR(false);
        // wac.setCopyWebInf(true);
        // wac.setProtectedTargets(new String[]{"/java", "/javax", "/org",
        // "/net", "/WEB-INF", "/META-INF"});
        wac.setTempDirectory(new File(conf.get(PROP_TEMP_DIR, "temp")));
        wac.setClassLoader(classLoader);
        wac.setConfigurationDiscovered(true);
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            wac.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        }

        List<Resource> resources = new ArrayList<>();
        for (String resourcePath : getResourcePaths()) {
            File f = new File(resourcePath);
            if (f.exists()) {
                resources.add(Resource.newResource(f));
            }
            Enumeration<URL> urls = appContext.getClassLoader().getResources(resourcePath);
            while (urls.hasMoreElements()) {
                resources.add(Resource.newResource(urls.nextElement()));
            }
        }
        if (resources.isEmpty()) {
            resources.add(Resource.newClassPathResource("META-INF/jetty_resources"));
        }
        if (conf.has(PROP_STATIC_PATH_LOCAL)) {
            File f = new File(conf.get(PROP_STATIC_PATH_LOCAL));
            if (f.exists()) {
                log.debug("found static local path, add it : " + f.getAbsolutePath());
                resources.add(0, Resource.newResource(f));
            } else {
                log.debug("static local path not exist, skip it : " + f.getPath());
            }
        }
        wac.setBaseResource(new ResourceCollection(resources.toArray(new Resource[resources.size()])) {
            @Override
            public Resource addPath(String path) throws IOException, MalformedURLException {
                // TODO 为啥ResourceCollection读取WEB-INF的时候返回null
                // 从而导致org.eclipse.jetty.webapp.WebAppContext.getWebInf()抛NPE
                // 先临时hack吧
                Resource resource = super.addPath(path);
                if (resource == null && "WEB-INF/".equals(path)) {
                    return Resource.newResource(new File("XXXX"));
                }
                return resource;
            }
        });
        if (conf.getBoolean(PROP_GZIP_ENABLE, false)) {
            GzipHandler gzip = new GzipHandler();
            gzip.setHandler(wac);
            gzip.setMinGzipSize(conf.getInt(PROP_GZIP_MIN_CONTENT_SIZE, 512));
            gzip.setCompressionLevel(conf.getInt(PROP_GZIP_LEVEL, Deflater.DEFAULT_COMPRESSION));
            server.setHandler(gzip);
        } else {
            server.setHandler(wac);
        }
        List<String> list = Configuration.ClassList.serverDefault(server);
        list.add("org.eclipse.jetty.annotations.AnnotationConfiguration");
        list.add("org.eclipse.jetty.webapp.MetaInfConfiguration");
        wac.setConfigurationClasses(list);
        wac.getServletContext().setExtendedListenerTypes(true);
        
        SessionHandler sessionHandler = wac.getSessionHandler();
        
        sessionHandler.setMaxInactiveInterval(getSessionTimeout());
        
        // cookie相关
        SessionCookieConfig cc = sessionHandler.getSessionCookieConfig();
        cc.setHttpOnly(conf.getBoolean(PROP_SESSION_COOKIE_HTTPONLY, false));
        cc.setSecure(conf.getBoolean(PROP_SESSION_COOKIE_SECURE, false));
        if (!Strings.isBlank(conf.get(PROP_SESSION_COOKIE_NAME)))
        	cc.setName(conf.get(PROP_SESSION_COOKIE_NAME).trim());
        if (!Strings.isBlank(conf.get(PROP_SESSION_COOKIE_DOMAIN)))
        	cc.setDomain(conf.get(PROP_SESSION_COOKIE_DOMAIN).trim());
        if (!Strings.isBlank(conf.get(PROP_SESSION_COOKIE_PATH)))
        	cc.setPath(conf.get(PROP_SESSION_COOKIE_PATH).trim());

        ErrorHandler ep = Lang.first(appContext.getBeans(ErrorHandler.class));
        if (ep == null) {
            ErrorPageErrorHandler handler = new ErrorPageErrorHandler();
            handler.setErrorPages(getErrorPages());
            ep = handler;
        }
        wac.setErrorHandler(ep);
        wac.setWelcomeFiles(getWelcomeFiles());
        wac.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

        updateMonitorValue("welcome_files", Strings.join(",", wac.getWelcomeFiles()));

        // 设置一下额外的东西
        server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", getMaxFormContentSize());
        updateMonitorValue("maxFormContentSize", server.getAttribute("org.eclipse.jetty.server.Request.maxFormContentSize"));
        server.setAttribute("org.eclipse.jetty.server.Request.maxFormKeys", getMaxFormKeys());
        wac.setMaxFormContentSize(getMaxFormContentSize());
        wac.setMaxFormKeys(getMaxFormKeys());
        server.setDumpAfterStart(false);
        server.setDumpBeforeStop(false);
        server.setStopAtShutdown(true);

        addNutzSupport();

        ServerContainer sc = WebSocketServerContainerInitializer.initialize(wac);
        for (Class<?> klass : Scans.me().scanPackage(appContext.getPackage())) {
            if (klass.getAnnotation(ServerEndpoint.class) != null) {
                sc.addEndpoint(klass);
            }
        }

        // 试试session持久化
        if (conf.getBoolean(PROP_SESSION_STORE_ENABLE)) {
            SessionHandler handler = wac.getSessionHandler();
            SessionCache sessionCache = new DefaultSessionCache(handler);
            String type = conf.get(PROP_SESSION_STORE_TYPE, "jdbc");
            log.info("using session store, type=" + type);
            switch (type) {
            case "jdbc": {
                JDBCSessionDataStoreFactory factory = new JDBCSessionDataStoreFactory();
                DatabaseAdaptor adaptor = new DatabaseAdaptor();
                adaptor.setDatasource(ioc.get(DataSource.class, conf.get(PROP_SESSION_JDBC_DATASOURCE_IOCNAME, "dataSource")));
                factory.setDatabaseAdaptor(adaptor);
                sessionCache.setSessionDataStore(factory.getSessionDataStore(handler));
                break;
            }
            case "file": {
                FileSessionDataStoreFactory factory = new FileSessionDataStoreFactory();
                factory.setStoreDir(new File(conf.get(PROP_SESSION_FILE_STOREDIR, "./sessions")));
                sessionCache.setSessionDataStore(factory.getSessionDataStore(handler));
                break;
            }
            case "ioc": {
                sessionCache.setSessionDataStore(ioc.get(SessionDataStore.class, conf.get(PROP_SESSION_IOC_DATASTORE, "jettySessionDataStore")));
                break;
            }
            case "redis": {
                // 未完成...
            }
            default:
                log.warn("not support yet, type=" + type);
                break;
            }
            handler.setSessionCache(sessionCache);
        }
        if (conf.getInt("jetty.sessionScavengeInterval.seconds") > 0) {

            SessionIdManager sessionIdManager = sessionHandler.getSessionIdManager();
            if (sessionIdManager == null) {
            	sessionIdManager = new DefaultSessionIdManager(server);
            	sessionHandler.setSessionIdManager(sessionIdManager);
            }
            HouseKeeper keeper = sessionIdManager.getSessionHouseKeeper();
            if (keeper == null) {
            	keeper = new HouseKeeper();
            	sessionIdManager.setSessionHouseKeeper(keeper);
            }
            keeper.setIntervalSec(conf.getInt("jetty.sessionScavengeInterval.seconds"));
            server.addBean(keeper, true);
        }
    }

    private void addNutzSupport() {
        wac.addEventListener(ioc.get(NbServletContextListener.class));
    }

    public int getMaxFormContentSize() {
        return conf.getInt(PROP_MAX_FORM_CONTENT_SIZE, 1024 * 1024 * 1024);
    }

    public int getMaxFormKeys() {
        return conf.getInt(PROP_MAX_FORM_KEYS, 1000);
    }

    public int getIdleTimeout() {
        return conf.getInt(PROP_IDLE_TIMEOUT, 300 * 1000);
    }

    public int getMinThreads() {
        return Lang.isAndroid ? 8 : conf.getInt(PROP_THREADPOOL_MINTHREADS, 200);
    }

    public int getMaxThreads() {
        return Lang.isAndroid ? 50 : conf.getInt(PROP_THREADPOOL_MAXTHREADS, 500);
    }

    public int getThreadPoolIdleTimeout() {
        return conf.getInt(PROP_THREADPOOL_TIMEOUT, 60 * 1000);
    }

    protected String getConfigurePrefix() {
        return PRE;
    }

    public String getMonitorName() {
        return "jetty";
    }
}

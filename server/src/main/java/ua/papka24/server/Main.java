/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ua.papka24.server;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.google.gson.Gson;
import com.sun.jersey.api.container.grizzly2.GrizzlyServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.net.httpserver.HttpServer;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.Upload;
import ua.papka24.server.api.helper.EmailQueueConsumer;
import ua.papka24.server.api.helper.EmailTimer;
import ua.papka24.server.api.helper.PDFRenderQueueConsumer;
import ua.papka24.server.db.redis.email.CustomPriorityQuery;
import ua.papka24.server.service.billing.BillingQueryManager;
import ua.papka24.server.utils.InfoFilter;
import ua.papka24.server.db.util.UpgradeUtil;
import ua.papka24.server.utils.websocket.WebSocketServer;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class Main {

    public static String nodeName = "unknown";
    public static final Gson gson = new Gson();
    public static final Logger log = LoggerFactory.getLogger("Main");
    public static final Properties property = new Properties();
    public static String passwordSalt;
    public static Queue<File> renderQueue = new ConcurrentLinkedQueue<>();
    public static CustomPriorityQuery emailQueue;
    private static Timer timer = new Timer();

    public static String certificateCacheDir;

    public static String CDNPath;
    public static String recaptchaSecret;

    public static String DOMAIN = "";

    public static Main main;
    public static GoogleAuthenticatorConfig gaConfig = (new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()).setWindowSize(5).build();
    public static GoogleAuthenticator gAuth = new GoogleAuthenticator(Main.gaConfig);

    public Main() {
        try {
            File root = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File coreConfigPath;
            File logConfigPath;
            if (root.isDirectory()) {
                coreConfigPath = new File(root, "config.properties");
                logConfigPath = new File(root, "logback.xml");
            } else {
                coreConfigPath = new File(root.getParent(), "config.properties");
                logConfigPath = new File(root.getParent(), "logback.xml");
            }
            InputStreamReader isr;
            if (Files.exists(coreConfigPath.toPath())) {
                isr = new InputStreamReader(new FileInputStream(coreConfigPath), "UTF-8");
                property.load(isr);
                log.warn("LOAD SYSTEM CORE CONFIG");
            } else {
                isr = new InputStreamReader(this.getClass().getResourceAsStream("/config.properties"), "UTF-8");
                property.load(isr);
                log.warn("LOAD INNER JAR CORE CONFIG");
            }
            try{isr.close();}catch (Exception ex){/*not*/}
            if (Files.exists(logConfigPath.toPath())) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

                try {
                    JoranConfigurator configurator = new JoranConfigurator();
                    configurator.setContext(context);
                    context.reset();
                    configurator.doConfigure(logConfigPath);
                    log.warn("LOAD CUSTOM LOGGER CONFIG");
                } catch (JoranException je) {
                    log.warn("LOAD INNER JAR LOGGER CONFIG");
                }
            } else {
                log.warn("LOAD INNER JAR LOGGER CONFIG");
            }
        } catch (Exception e) {
            log.error("Не найден файл параметров");
            return;
        }
        nodeName = property.getProperty("nodeName","unknown");

        passwordSalt = property.getProperty("passwordSalt");
        recaptchaSecret = property.getProperty("recaptcha.secret");
        CDNPath = property.getProperty("CDN.path");
        if (CDNPath == null || !Files.isDirectory(Paths.get(CDNPath))) {
            log.error("Каталог для ресурсов не существует, или не указан в config.properties.");
            return;
        }

        if (!Files.isWritable(Paths.get(CDNPath))) {
            log.error("В каталог для ресурсов нет прав на запись.");
        }
        certificateCacheDir = property.getProperty("certificateCacheDir");

        if (Files.notExists(Paths.get(certificateCacheDir))) {
            try {
                Files.createDirectory(Paths.get(certificateCacheDir));
            } catch (IOException e) {
                log.error("Can't create directory {}  for certificates", certificateCacheDir, e);
            }
        }

        if (!Files.isWritable(Paths.get(certificateCacheDir))) {
            log.error("Can't write to directory {}", certificateCacheDir);
        }
    }

    public static void main(String[] args) {
        try {
            main = new Main();
            DOMAIN = property.getProperty("server.domain", "papka24.com.ua");

            //todo упаковать сервисные потоки в менеджер
            //толкьо одна нода занимаеться обновлением и апгрейдом
            if(property.getProperty("iamrefreshes", "false").equals("true")) {
                try {
                    UpgradeUtil upgradeUtil = new UpgradeUtil();
                    upgradeUtil.upgrade();
                }catch (Exception ex){
                    log.warn("error upgrade database",ex);
                }
                BillingQueryManager.startTimer();

                if (property.getProperty("enableStatistic", "true").equals("true")) {
                    log.info("enable statistic true");
                    Calendar calendar = Calendar.getInstance(new Locale("uk","UA"));
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 2);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    log.info("statistic builder start at : {}", calendar.getTime());
                }
            }

            if (property.getProperty("enableSpam", "false").equals("true")) {
                timer.schedule(new EmailTimer(), 5000, TimeUnit.HOURS.toMillis(2));
            }

            org.glassfish.grizzly.http.server.HttpServer server;
            HttpServer fileServer;

            boolean needUseRedisEmail = property.getProperty("redis.email","true").equals("true");
            new Thread(new PDFRenderQueueConsumer(renderQueue)).start();
            new Thread(new EmailQueueConsumer(emailQueue = CustomPriorityQuery.getQuery(needUseRedisEmail), property.getProperty("emailServer.path", "localhost"),
                    Integer.parseInt(property.getProperty("emailServer.port", "25")),
                    property.getProperty("emailServer.username", null),
                    property.getProperty("emailServer.password", null))).start();

            new Thread(new BillingQueryManager()).start();

            ResourceConfig rc = new PackagesResourceConfig("ua.papka24.server.api");
            rc.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, true);

            rc.getContainerRequestFilters().add(InfoFilter.class);
            //
            server = GrizzlyServerFactory.createHttpServer(UriBuilder.fromUri("http://" + property.getProperty("server.localDomain", "localhost") + property.getProperty("server.path")).port(Integer.valueOf(property.getProperty("server.port", "9999"))).build(), rc);
            NetworkListener listener = server.getListeners().iterator().next();
            listener.createManagementObject();
            server.removeListener(listener.getName());
            final TCPNIOTransport tcpnioTransport = listener.getTransport();
            int cores = Runtime.getRuntime().availableProcessors();
            ThreadPoolConfig tcpConfig = tcpnioTransport.getWorkerThreadPoolConfig().copy()
                .setQueueLimit(10000)
                .setCorePoolSize(cores)
                .setMaxPoolSize(cores * 3);
            tcpnioTransport.setWorkerThreadPoolConfig(tcpConfig);
            listener.setTransport(tcpnioTransport);
            server.addListener(listener);
            server.start();
            log.warn("REST SERVER WAS STARTED...");
            log.error("max pool size set to:{}",server.getListeners().iterator().next().getTransport().getWorkerThreadPoolConfig().getMaxPoolSize());

            // Режим сервера
            fileServer = HttpServer.create(new InetSocketAddress(property.getProperty("server.localDomain", "localhost"), Integer.valueOf(property.getProperty("server.upload.port"))), 0);
            fileServer.createContext(property.getProperty("server.upload.path"), new Upload());
            fileServer.setExecutor(null); // creates a default executor
            fileServer.start();
            log.warn("CDN CONTROL SERVER WAS STARTED...");

            //web socket server
            if(property.getProperty("webSocket.enable","false").equals("true")) {
                WebSocketServer webSocketServer = new WebSocketServer(new InetSocketAddress(property.getProperty("server.localDomain", "localhost"), Integer.valueOf(property.getProperty("webSocket.port", "9999"))));
                webSocketServer.start();
                log.warn("WEB SOCKET SERVER WAS STARTED...");
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
            log.error("error ", throwable);
        }
    }
}

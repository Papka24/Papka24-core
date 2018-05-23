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

package ua.papka24.server.utils.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.api.helper.CompanyHelper;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.db.dto.FilterDTO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.utils.datetime.DateTimeUtils;
import ua.papka24.server.utils.exception.ReceivingDataException;
import ua.papka24.server.utils.exception.ServerException;
import ua.papka24.server.utils.websocket.data.WebSocketRequest;
import ua.papka24.server.utils.websocket.data.WebSocketResponse;

import java.lang.ref.SoftReference;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class WebSocketServer extends org.java_websocket.server.WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);
    private static final ExpiringMap<String, Map<String, List<WebSocket>>> userSessionSockets = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.ACCESSED).asyncExpirationListener(new UserExpirationListener())
            .expiration(15, TimeUnit.MINUTES).build();
    private static final Gson gson = new Gson();
    private static final long ONE_WEEK = DateTimeUtils.ONE_WEEK;
    private static final Timer pingTimer = new Timer(true);
    private static final long PING_TIMER = DateTimeUtils.SECONDS_30;
    private final boolean timerEnabled = Main.property.getProperty("webSocket.timer","true").equals("true");
    public static Long historyControlTime;

    public WebSocketServer(InetSocketAddress address) {
        super(address);
        historyControlTime = Long.valueOf(Main.property.getProperty("historyControlTime", "1477312984000"));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if(timerEnabled) {
            pingTimer.schedule(new PingPongTimerTask(conn), PING_TIMER, PING_TIMER);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    }

    private static class MessageType {
        public String type;
    }

    private void notification(WebSocket conn, String message){
        WebSocketRequest request = gson.fromJson(message, WebSocketRequest.class);
        if (request == null) {
            return;
        }
        Session userSession = SessionsPool.find(request.sessionid);
        if (userSession == null) {
            log.warn("session not found:{}. goodbye", request.sessionid);
            conn.close();
            return;
        }
        String userLogin = userSession.getUser().getLogin();
        Map<String, List<WebSocket>> userSessionSockets = WebSocketServer.userSessionSockets.computeIfAbsent(userLogin, k -> new HashMap<>());
        List<WebSocket> sessionSockets = userSessionSockets.computeIfAbsent(userSession.getSessionId(), k -> new ArrayList<>());
        if(!sessionSockets.contains(conn)){
            sessionSockets.add(conn);
        }

        FilterDTO filter = new FilterDTO();
        if(request.limit!=null && (request.page!=null || request.offset!=null)) {
            filter.setLimit(request.limit);
            filter.setPage(request.page);
            filter.setOffset(request.offset);
        }
        long timestamp = System.currentTimeMillis();
        WebSocketResponse response = new WebSocketResponse();
        response.timestamp = timestamp;
        sendMessage(conn, userLogin, gson.toJson(response));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            if (message != null) {
                log.debug("wss message:{}", message);
                MessageType mm =gson.fromJson(message, MessageType.class);
                if(mm.type!=null) {
                    switch (mm.type) {
                        default:
                            notification(conn, message);
                            break;
                    }
                }else{
                    notification(conn, message);
                }
            }
        }catch (JsonSyntaxException jse){
            log.warn("incorrect json request:{} from:{}", message, conn.getLocalSocketAddress());
        } catch (Exception ex) {
            log.error("error on message process", ex);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String message = ex.getMessage();
        if(message!=null && !message.contains("Connection reset by peer")){
            log.error("websocket error:" + conn, ex);
        }
    }

    public static void sendMessage(String userLogin, ResourcesChangeEvent eventMessage) {
        WebSocketResponse response = WebSocketResponse.parseEvent(eventMessage);
        response.timestamp = eventMessage.getTime();
        if("DELETE".equals(eventMessage.getEventType())){
            response.resourceId = eventMessage.getDocID();
        }
        sendMessage(null, userLogin, gson.toJson(response));
    }

    public static void sendMessage(String userLogin, ResourcesChangeEvent eventMessage, WebSocketResponse.Method method){
        WebSocketResponse response = WebSocketResponse.parseEvent(eventMessage);
        response.timestamp = eventMessage.getTime();
        if("DELETE".equals(eventMessage.getEventType())){
            response.resourceId = eventMessage.getDocID();
            response.method = method;
            sendMessage(null, userLogin, gson.toJson(response));
        }else{
            if(eventMessage.getDocID()!=null) {
                response.resourceId = eventMessage.getDocID();
                response.method = method;
                sendMessage(null, userLogin, gson.toJson(response));
            }else {
                response.method = method;
                sendMessage(null, userLogin, gson.toJson(response));
            }
        }
    }

    public static void sendReset(String event, String userLogin, Long companyId){
        log.info("sendReset:{}",userLogin);
        WebSocketResponse response = new WebSocketResponse();
        response.method = WebSocketResponse.Method.RESET;
        response.timestamp = new Date().getTime();
        response.data = ResourceDAO.getInstance().search(null, userLogin, null, companyId);
        response.userLogin = userLogin;
        response.eventType = event;
        sendMessage(null, userLogin, gson.toJson(response));
    }

    public static boolean sendMessage(String userLogin, String message){
        return sendMessage(null, userLogin, message);
    }

    private static boolean sendMessage(WebSocket webSocket, String userLogin, String message) {
        boolean res = false;
        try {
            if (webSocket != null) {
                webSocket.send(message);
                return true;
            }
            List<WebSocket> webSockets = new ArrayList<>();
            Map<String, List<WebSocket>> userSockets = userSessionSockets.get(userLogin);
            if(userSockets!=null) {
                userSockets.entrySet().stream().map(Map.Entry::getValue).forEach(webSockets::addAll);
            }else{
                return false;
            }

            Iterator<WebSocket> iterator = webSockets.iterator(); //todo concurrent возможно !
            while (iterator.hasNext()) {
                WebSocket socket = iterator.next();
                try {
                    if (socket.isOpen()) {
                        socket.send(message);
                        res = true;
                    } else {
                        iterator.remove();
                        webSockets.remove(socket);
                    }
                }catch (WebsocketNotConnectedException wnce){
                    if(socket!=null){
                        try{
                            iterator.remove();
                            webSockets.remove(socket);
                            socket.close();
                        }catch (Exception ex){log.warn("pingpongtimer error:",ex);}
                    }
                    log.warn("WebsocketNotConnectedException : {}", userLogin);
                }catch (Exception ex) {
                    log.warn("some send error", ex);
                }
            }
        }catch (WebsocketNotConnectedException wnce){
            if(webSocket!=null){
                try{webSocket.close();}catch (Exception ex){log.warn("pingpongtimer error:",ex);}
            }
        }
        return res;
    }

    public static void sendMassReset() {
        List<String> allUsers = UserDAO.getInstance().list();
        allUsers.forEach(u->{
            UserDTO user = UserDTO.load(u);
            if(user!=null){
                sendReset("RESET",user.getLogin(),user.getCompanyId());
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    log.warn("interrupted exception : {}", e);
                }
            }
        });
    }

    public static void sendDeleteMessage(String userLogin, ResourcesChangeEvent notifierMsg) {
        WebSocketResponse response = new WebSocketResponse();
        response.resourceId = notifierMsg.getDocID();
        response.timestamp = notifierMsg.getTime();
        response.method = WebSocketResponse.Method.DELETE;
        sendMessage(null, userLogin, gson.toJson(response));
    }

    private static class UserExpirationListener implements ExpirationListener<String, Map<String, List<WebSocket>>> {

        @Override
        public void expired(String key, Map<String, List<WebSocket>> sessionSockets) {
            try {
                if (sessionSockets != null) {
                    Collection<List<WebSocket>> sockets = sessionSockets.values();
                    for (List<WebSocket> socketList : sockets) {
                        Iterator<WebSocket> iterator = socketList.iterator();
                        while (iterator.hasNext()) {
                            WebSocket socket = iterator.next();
                            if(socket!=null && socket.isOpen()){
                                socket.close();
                                iterator.remove();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("error", ex);
            }
        }
    }

    public static void logoutUser(Session session){
        String sessionId = session.getSessionId();
        String userLogin = session.getUser().getLogin();
        List<WebSocket> sessionSockets = userSessionSockets
                .computeIfAbsent(userLogin, k -> new HashMap<>())
                .computeIfAbsent(sessionId, k -> new ArrayList<>());
        Iterator<WebSocket> iterator = sessionSockets.iterator();
        while(iterator.hasNext()){
            WebSocket socket = iterator.next();
            socket.close();
            iterator.remove();
        }
    }

    private static class PingPongTimerTask extends TimerTask{

        private SoftReference<WebSocket> socketReference;

        private PingPongTimerTask(WebSocket socketReference){
            this.socketReference = new SoftReference<>(socketReference);
        }

        @Override
        public void run() {
            WebSocket webSocket = socketReference.get();
            try {
                if (webSocket != null && webSocket.isOpen()) {
                    WebSocketResponse response = new WebSocketResponse();
                    response.status = "OK";
                    webSocket.send(gson.toJson(response));
                } else {
                    this.cancel();
                }
            }catch (WebsocketNotConnectedException wnce){
                if(webSocket!=null){
                    try{webSocket.close();}catch (Exception ex){log.warn("pingpongtimer error:",ex);}
                }
            }catch (Exception ex){
                log.warn("pingpongtimer error:",ex);
            }
        }
    }
}
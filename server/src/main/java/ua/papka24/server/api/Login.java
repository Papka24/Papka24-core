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

package ua.papka24.server.api;

import com.google.gson.Gson;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.DTO.LoginDataDTO;
import ua.papka24.server.api.DTO.UserDescriptionDTO;
import ua.papka24.server.api.DTO.UserInfoDTO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.Main;
import ua.papka24.server.db.dao.SpamDAO;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.utils.logger.Event;
import ua.papka24.server.utils.websocket.WebSocketServer;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Path("login")
public class Login extends REST {
    private static SecureRandom random = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger("login");
    private static ExpiringMap<String,Integer> loginsAttempt = ExpiringMap.builder().expiration(5, TimeUnit.MINUTES).expirationPolicy(ExpirationPolicy.CREATED).build();
    private static Gson gson = new Gson();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@HeaderParam("sessionid") String sessionId) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            return Response.ok(UserInfoDTO.gson.toJson(new UserInfoDTO(s.getUser(), sessionId, UserDAO.getInstance().getFriends(s.getUser())))).build();
        }
    }

    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSessions(@HeaderParam("sessionid") String sessionId) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            String login = s.getUser().getLogin();
            HashMap<String, Set<String>> result = new HashMap<>();

            result.put("pool", SessionsPool.getUserSession(login));
            Set<String> userSessions = RedisDAOManager.getInstance().getUserSessions(login);
            if(userSessions!=null) {
                result.put("redis", userSessions);
            }
            return Response.ok(gson.toJson(result)).build();
        }
    }

    @GET
    @Path("/status")
    public Response getStatus(@HeaderParam("sessionid") String sessionId) {
        return SessionsPool.checkSession(sessionId)?OK_EMPTY:ERROR_NOT_FOUND;
    }

    @GET
    @Path("/cloud/{mode}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enablePrivatCloud(@HeaderParam("sessionid") String sessionId, @PathParam("mode") String mode) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            try {
                UserDescriptionDTO ud = UserDescriptionDTO.gson.fromJson(s.getUser().getDescription());
                ud.setCloudOn(Objects.equals(mode, "on"));
                s.getUser().setDescription(UserDescriptionDTO.gson.toJson(ud));
                UserDAO.getInstance().changeDescription(s.getUser().getLogin(), s.getUser().getDescription());
                RedisDAOManager.getInstance().markChanged(s.getUser().getLogin());
                return Response.ok().build();
            } catch (Exception e) {
                return ERROR_SERVER;
            }
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@HeaderParam("sessionid") String sessionId) {
        log.info("close session:{}",sessionId);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            SessionsPool.closeSession(s.getSessionId());
            WebSocketServer.logoutUser(s);
            return Response.ok().build();
        }
    }

    @GET
    @Path("/email/{email}")
    @Produces(MediaType.TEXT_HTML)
    public Response deleteSpam(@PathParam("email") String email) {
        log.info("delete spam:{}", email, Event.DELETE_SPAM);
        if (SpamDAO.getInstance().deleteUserFromSpam(email)) {
            return Response.ok("<html><head><meta charset=\"UTF-8\"></head><body>Ми більше не будемо відправляти вам такі email’и.</body></html>").build();
        } else {
            return ERROR_BAD_REQUEST;
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@HeaderParam("v") String frontVersion, String jsonRequest) {
        LoginDataDTO data;
        try {
            data = LoginDataDTO.gson.fromJson(jsonRequest);
        } catch (Exception e) {
            return ERROR_BAD_REQUEST;
        }
        if (data == null || data.getLogin() == null || data.getPassword() == null) {
            return ERROR_BAD_REQUEST;
        }
        data.trim();
        //проверка на не превышение допустимого количества ошибок логина пользователя
        //до проверки поиска пользователя - обезопасить от левых попыток логина
        Integer attempt = loginsAttempt.get(data.getLogin());
        int maxAttempt = Integer.valueOf(Main.property.getProperty("lfrequency.limit","10"));
        if(attempt==null){
            attempt = RedisDAOManager.getInstance().incrementAndGetLoginAttempt(data.getLogin());
        }else{
            attempt++;
        }
        loginsAttempt.put(data.getLogin(),attempt);
        RedisDAOManager.getInstance().incrementAndGetLoginAttempt(data.getLogin());
        if(attempt > maxAttempt){
            log.info("to many login attempt user:{} blocked", data.getLogin());
            return ERROR_TOO_MANY_REQUESTS;
        }

        UserDTO user;
        if (frontVersion == null) {
            user = UserDAO.getInstance().getEmptyUser(data.getLogin());
        } else {
            user = UserDAO.getInstance().getUser(data.getLogin());
        }
        if (user == null) {
            return ERROR_NOT_FOUND;
        } else if (user.checkPassword(data.getPassword())) {
            if (!user.checkOTP(data)) {
                loginsAttempt.put(user.getLogin(), attempt);
                return ERROR_LOCKED;
            }
            Session session = SessionsPool.openSession(user, frontVersion == null ? Boolean.TRUE : Boolean.FALSE);
            if (session == null) {
                log.info("login unsuccessful:{}", data.getLogin());
                loginsAttempt.put(user.getLogin(), attempt);
                return ERROR_SERVER;
            } else {
                log.info("login successful:{}:{}", session.getSessionId(), data.getLogin());
                if (loginsAttempt.containsKey(user.getLogin())) {
                    loginsAttempt.remove(user.getLogin());
                }
                UserInfoDTO userInfo = new UserInfoDTO(session.getUser(), session.getSessionId(), (frontVersion!=null?UserDAO.getInstance().getFriends(session.getUser()):null));
                RedisDAOManager.getInstance().clearLoginAttempt(user.getLogin());
                return Response.ok(UserInfoDTO.gson.toJson(userInfo)).build();
            }
        } else {
            return ERROR_UNAUTHORIZED;
        }
    }

    @GET
    @Path("otp")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createNewOTP(@HeaderParam("sessionid") String sessionId) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            byte[] buffer = new byte[10];
            random.nextBytes(buffer);
            String key =  new Base32().encodeToString(buffer);
            return Response.ok(key).build();
        }
    }

    @PUT
    @Path("otp/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateNewOTP(@HeaderParam("sessionid") String sessionId, @PathParam("code") String code, String key) {
        Session s = SessionsPool.find(sessionId);

        if (code == null || code.length()!=6) {
            return ERROR_BAD_REQUEST;
        } else if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            GoogleAuthenticator gAuth = new GoogleAuthenticator(Main.gaConfig);
            if (gAuth.authorize(key, Integer.valueOf(code))) {
                s.getUser().setAuthData(key);
                int result = UserDAO.getInstance().enableOTP(s.getUser());
                if (result > 0) {
                    RedisDAOManager.getInstance().markChanged(s.getUser().getLogin());
                    SessionsPool.setPrimeSession(s.getUser().getLogin(), sessionId);
                    return Response.ok().build();
                }
            }
            return ERROR_BAD_REQUEST;
        }
    }

    @DELETE
    @Path("otp")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deactivateOTP(@HeaderParam("sessionid") String sessionId) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            int result = UserDAO.getInstance().disableOTP(s.getUser().getLogin());
            if (result > 0) {
                s.getUser().setAuthData(null);
                RedisDAOManager.getInstance().markChanged(s.getUser().getLogin());
                SessionsPool.setPrimeSession(s.getUser().getLogin(), sessionId);
                return Response.ok().build();
            }
            return ERROR_BAD_REQUEST;
        }
    }

    @DELETE
    @Path("wizard")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deactivateWizard(@HeaderParam("sessionid") String sessionId) {
        log.info("deactivateWizard");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            UserDAO.getInstance().deactivateWizard(s.getUser());
            RedisDAOManager.getInstance().markChanged(s);
            return Response.ok().build();
        }
    }

    @PUT
    @Path("password")
    @Produces(MediaType.APPLICATION_JSON)
    public Response changePassword(@HeaderParam("sessionid") String sessionId, String newPass) {
        log.info("changePassword");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            UserDAO.getInstance().changePassword(s.getUser().getLogin(), newPass);
            return Response.ok().build();
        }
    }

    @PUT
    @Path("name")
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeName(@HeaderParam("sessionid") String sessionId, String name) {
        log.info("changeName");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            name = name.replace("<", "&#60;").replace(">", "&#62;");
            if (name.length() > 32) {
                name = name.substring(32);
            }
            UserDAO.getInstance().changeFullName(s.getUser().getLogin(), name);
            s.getUser().setFullName(name);
            RedisDAOManager.getInstance().markChanged(s);
            return Response.ok().build();
        }
    }

    @PUT
    @Path("description")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setDescription(@HeaderParam("sessionid") String sessionId, String description) {
        log.info("description");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            UserDTO user = s.getUser();
            user.setDescription(description);
            UserDAO.getInstance().changeDescription(user.getLogin(), description);
            RedisDAOManager.getInstance().markChanged(s);
            return Response.ok(UserInfoDTO.gson.toJson(new UserInfoDTO(user))).build();
        }
    }

    @PUT
    @Path("/tags/{tagReset}")
    public Response setTag(@HeaderParam("sessionid") String sessionId, @PathParam("tagReset") Long tagReset, String newTags) {
        log.info("tags");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            int result = UserDAO.getInstance().updateTags(s.getUser(), tagReset, newTags);
            if (result < 1) {
                return ERROR_NOT_FOUND;
            } else {
                RedisDAOManager.getInstance().markChanged(s);
                return Response.ok().build();
            }
        }
    }
}
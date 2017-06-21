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
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.dao.CompanyDAO;
import ua.papka24.server.db.dto.EmployeeDTO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.CompanyDTO;
import ua.papka24.server.api.helper.CompanyHelper;
import ua.papka24.server.api.helper.EmailHelper;
import ua.papka24.server.db.dao.UserDAO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.utils.exception.ServerException;
import ua.papka24.server.utils.json.LowerStringDeserializer;

import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Path("company")
public class Company extends REST {

    private static final Logger log = LoggerFactory.getLogger("company");
    public static final ExpiringMap<GroupInviteDTO, Object> invites = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.CREATED)
            .expiration(5, TimeUnit.MINUTES).build();
    private static final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(String.class, new LowerStringDeserializer());
    private static final long TO_MANY_REQUEST = -2;
    private static final long USER_ALREADY_BUSY = -1;


    @POST
    @Path("/create")
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@HeaderParam("sessionid") String sessionId, String companyName) {
        CompanyDTO ci;
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            companyName = companyName.replace("<", "&#60;").replace(">", "&#62;");
            UserDTO user = s.getUser();
            if (user.getCompanyDTO() == null && (user.getCompanyId() == null)) {
                ci = CompanyDAO.getInstance().createCompany(s.getUser(), companyName);
                if(ci==null){
                    return ERROR_SERVER;
                }
                UserDAO.getInstance().setCompany(s.getUser(), ci.getCompanyId());
                RedisDAOManager.getInstance().markChanged(s);
                return Response.ok(CompanyDTO.gson.toJson(ci)).build();
            } else {
                return ERROR_FORBIDDEN;
            }
        }
    }

    @POST
    @Path("/invite")
    @Produces(MediaType.APPLICATION_JSON)
    public Response add(@HeaderParam("sessionid") String sessionId, String invitesRequest) {
        log.info("request to /invite accepted.");
        Session s = SessionsPool.find(sessionId);
        log.info("session get.");
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            log.info("invite new user to group");
            if (invitesRequest == null || invitesRequest.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            List<GroupInviteDTO> groupInvites = builder.create().fromJson(invitesRequest, new TypeToken<List<GroupInviteDTO>>() {
            }.getType());
            if (groupInvites == null || groupInvites.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            UserDTO manager = s.getUser();
            if (manager != null) {
                Optional<EmployeeDTO> managerInfo = manager.getCompanyDTO().getEmployee().stream().filter(e -> manager.getLogin().equals(e.getLogin())).findAny();
                log.info("manager info get.");
                if (managerInfo.isPresent() && (managerInfo.get().getRole() == EmployeeDTO.ROLE_ADMIN)) {
                    Map<String, Long> rejectedEmails = new HashMap<>();
                    groupInvites = groupInvites.stream().filter(e->EmailHelper.validate(e.getEmail())).collect(Collectors.toList());
                    log.info("Invites get.");
                    Map<String, Long> usersExists = CompanyDAO.getInstance().getUsersCompany(groupInvites.stream().map(GroupInviteDTO::getEmail).toArray(String[]::new));
                    log.info("userExists:{}", usersExists);
                    groupInvites.stream().filter(inv -> {
                        Long us = usersExists.get(inv.getEmail());
                        if ((us != null && us > 0)) {
                            if (!us.equals(manager.getCompanyId())) {
                                rejectedEmails.put(inv.getEmail(), USER_ALREADY_BUSY);
                            }
                            return false;
                        } else {
                            return true;
                        }
                    }).filter(inv -> !inv.getEmail().equals(manager.getLogin())).forEach(invite -> {
                        invite.setInitiator(manager.getLogin());
                        invite.setCompanyId(manager.getCompanyId());
                        String secret = EmailHelper.getEncryptedCompanyInvite(invite);
                        if (!invites.containsKey(invite) && !RedisDAOManager.getInstance().companyInviteExists(invite)) {
                            invites.put(invite, null);
                            RedisDAOManager.getInstance().saveCompanyInvite(invite);
                            boolean register = usersExists.keySet().contains(invite.getEmail());
                            boolean successInvite = CompanyDAO.getInstance().createEmployee(manager, invite.getEmail(), invite.getRole());
                            if(successInvite){
                                EmailHelper.sendInviteToGroupEmail(manager, invite.getEmail(), register, secret, invite.getRole());
                            }else{
                                rejectedEmails.put(invite.getEmail(), USER_ALREADY_BUSY);
                            }
                        } else {
                            rejectedEmails.put(invite.getEmail(), TO_MANY_REQUEST);
                        }
                    });
                    log.info("Invites filtered.");
                    List<String> filtredEmails = groupInvites.stream().map(GroupInviteDTO::getEmail).filter(email -> !manager.getFriends().containsKey(email)).collect(Collectors.toList());
                    log.info("Emails get.");
                    UserDAO.getInstance().addFriends(manager, filtredEmails);
                    log.info("Friends added.");
                    List<EmployeeDTO> companyEmployees = CompanyDAO.getInstance().getCompanyEmployees(manager.getCompanyId());
                    log.info("Employees get.");
                    manager.getCompanyDTO().setEmployee(companyEmployees);
                    log.info("Employees added.");
                    RedisDAOManager.getInstance().markChanged(s);
                    rejectedEmails.forEach((key, value) -> {
                        Long cid = -1L;
                        if (usersExists.get(key) != null) {
                            cid = usersExists.get(key);
                        }
                        EmployeeDTO em = new EmployeeDTO(key, cid, value, value, value, value, "");
                        companyEmployees.add(em);
                    });
                    log.info("Send OK response.");
                    return Response.ok().entity(new Gson().toJson(companyEmployees)).build();
                }
            }
            return ERROR_BAD_REQUEST;
        }
    }

    @GET
    @Path("/acceptInvite")
    @Produces(MediaType.APPLICATION_JSON)
    public Response acceptInvite(@QueryParam("secret") String secret) {
        GroupInviteDTO invite = EmailHelper.getDecryptedCompanyInvite(secret);
        if(invite==null){
            return ERROR_SERVER;
        }
        log.info("acceptInvite:{}:{}", invite.getEmail(), invite.getCompanyId());
        if (invite.getEmail() != null) {
            UserDTO user = UserDTO.load(invite.getEmail());
            try {
                CompanyDAO companyDAO = CompanyDAO.getInstance();
                boolean accepted = companyDAO.setStatus(invite.getEmail(), invite.getCompanyId(), EmployeeDTO.STATUS_ACCEPTED);
                if(!accepted){
                    log.info("can't accepted invite:{} company:{}", invite.getEmail(), invite.getCompanyId());
                    return Response.ok().entity("Вибачте, заявка не э дiйсна").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN+"; charset=UTF8" ).build();
                }
                if (user != null) {
                    if (user.getCompanyId() != null) {
                        return Response.temporaryRedirect(new URI("https://" + Main.DOMAIN)).build();
                    }
                    UserDAO.getInstance().setCompany(user, invite.getCompanyId());
                    RedisDAOManager.getInstance().markChanged(user.getLogin());
                    ResourcesChangeEvent notification = Notification.builder().eventType(EventType.JOIN_COMPANY)
                            .eventData(user.getLogin()).userLogin(invite.getInitiator())
                            .companyId(invite.getCompanyId()).createCompanyChangeNotification();
                    EventManager.getInstance().addNotification(notification);
                }
                EmailHelper.sendAcceptInviteToGroupEmail(invite.getEmail(), invite.getInitiator());
                List<EmployeeDTO> companyManagers = companyDAO.getCompanyEmployees(invite.getCompanyId(), EmployeeDTO.ROLE_ADMIN);
                companyManagers.forEach(e -> RedisDAOManager.getInstance().markChanged(e.getLogin()));
                return Response.temporaryRedirect(new URI("https://" + Main.DOMAIN)).build();
            } catch (Exception ex) {
                log.error("error accept invite", ex);
                return ERROR_SERVER;
            }
        } else {
            return ERROR_NOT_FOUND;
        }
    }

    @GET
    @Path("/rejectInvite")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rejectInvite(@QueryParam("secret") String secret) {
        GroupInviteDTO invite = EmailHelper.getDecryptedCompanyInvite(secret);
        if(invite==null){
            return ERROR_SERVER;
        }
        log.info("rejectInvite:{}:{}",invite.getEmail(),invite.getCompanyId());
        if (invite.getEmail() != null) {
            try {
                CompanyDAO.getInstance().setStatus(invite.getEmail(), invite.getCompanyId(), EmployeeDTO.STATUS_REJECTED);
                List<EmployeeDTO> companyManagers = CompanyDAO.getInstance().getCompanyEmployees(invite.getCompanyId(), EmployeeDTO.ROLE_ADMIN);
                companyManagers.forEach(e -> RedisDAOManager.getInstance().markChanged(e.getLogin()));
                return Response.temporaryRedirect(new URI("https://" + Main.DOMAIN)).build();
            } catch (Exception ex) {
                log.error("error accept invite", ex);
                return ERROR_SERVER;
            }
        } else {
            return ERROR_NOT_FOUND;
        }
    }

    @DELETE
    @Path("/remove/{login}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response remove(@HeaderParam("sessionid") String sessionId, @PathParam("login") String userLogin) {
        log.info("prepare to leave group:{}", userLogin);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if (userLogin == null || userLogin.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            UserDTO manager = s.getUser();
            log.info("remove user from group manager:{} companyId:{} user:{}", manager.getLogin(), manager.getCompanyId(), userLogin);
            long role = CompanyDAO.getInstance().getRole(manager.getLogin(), manager.getCompanyId());
            if (role != EmployeeDTO.ROLE_ADMIN && !manager.getLogin().equals(userLogin)) {
                return ERROR_FORBIDDEN;
            }
            if (role == EmployeeDTO.ROLE_ADMIN && manager.getLogin().equals(userLogin)) {
                List<String> admins = CompanyDAO.getInstance().getAdmins(manager.getCompanyId());
                if (admins.size() == 1) {
                    return ERROR_FORBIDDEN;
                }
            }
            CompanyDAO.getInstance().removeCompanyEmployee(manager.getLogin(), manager.getCompanyId(), userLogin);
            UserDTO user = UserDTO.load(userLogin);
            if (user != null) {
                CompanyDAO.getInstance().leaveCompany(user, manager.getCompanyId());
                //в случае покидания группы пользователь по сути теряет документы свои, та что вычищаем подписки
                EventManager.getInstance().removeSubscriber(manager.getCompanyId(), user);
            }
            List<EmployeeDTO> companyEmployees = CompanyDAO.getInstance().getCompanyEmployees(manager.getCompanyId());
            manager.getCompanyDTO().setEmployee(companyEmployees);
            RedisDAOManager.getInstance().markChanged(s);
            companyEmployees.forEach(l -> RedisDAOManager.getInstance().markChanged(l.getLogin()));
            ResourcesChangeEvent notification = Notification.builder().eventType(EventType.LEAVE_COMPANY).eventData(userLogin).userLogin(manager.getLogin()).companyId(manager.getCompanyId()).createCompanyChangeNotification();
            EventManager.getInstance().addNotification(notification);
            return Response.ok().entity(new Gson().toJson(companyEmployees)).build();
        }
    }

    @DELETE
    @Path("drop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dropCompany(@HeaderParam("sessionid") String sessionId) {
        log.info("drop company:{}", sessionId);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            try {
                UserDTO manager = s.getUser();
                if (manager == null) {
                    return ERROR_SERVER;
                }
                long role = CompanyDAO.getInstance().getRole(manager.getLogin(), manager.getCompanyId());
                if (role != EmployeeDTO.ROLE_ADMIN) {
                    return ERROR_FORBIDDEN;
                }
                log.info("drop company:{} manager:{}", manager.getCompanyId(), manager);
                List<String> admins = CompanyDAO.getInstance().getAdmins(manager.getCompanyId());
                if (admins.size() == 1) {
                    List<EmployeeDTO> employee = manager.getCompanyDTO().getEmployee();
                    EventManager.getInstance().removeCompanySubscribers(manager.getCompanyId());
                    int[] ints = CompanyDAO.getInstance().dropGroup(manager.getCompanyId());
                    log.info("drop company result:{}", ints);
                    employee.forEach(emp -> RedisDAOManager.getInstance().markChanged(emp.getLogin()));
                    employee.stream().filter(e->e!=null && e.getLogin()!=null).forEach(emp->{
                        ResourcesChangeEvent notification = Notification.builder().eventType(EventType.JOIN_COMPANY).eventData(emp.getLogin()).userLogin(manager.getLogin()).companyId(manager.getCompanyId()).createCompanyChangeNotification();
                        EventManager.getInstance().addNotification(notification);
                    });
                    return Response.ok().entity(new Gson().toJson(ints)).build();
                } else {
                    return ERROR_FORBIDDEN;
                }
            } catch (Exception ex) {
                log.error("error:", ex);
            }
            return ERROR_SERVER;
        }
    }

    @PUT
    @Path("role")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setRole(@HeaderParam("sessionid") String sessionId, String invitesRequest) {
        log.info("change employee role");
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if (invitesRequest == null || invitesRequest.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            List<GroupInviteDTO> groupInvites = new Gson().fromJson(invitesRequest, new TypeToken<List<GroupInviteDTO>>() {
            }.getType());
            if (groupInvites == null || groupInvites.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            UserDTO manager = s.getUser();
            if (manager != null) {
                Optional<EmployeeDTO> managerInfo = manager.getCompanyDTO().getEmployee().stream().filter(e -> manager.getLogin().equals(e.getLogin())).findAny();
                if (managerInfo.isPresent() && (managerInfo.get().getRole() == EmployeeDTO.ROLE_ADMIN)) {
                    groupInvites.forEach(inv -> CompanyDAO.getInstance().setRole(inv.getEmail(), manager.getCompanyId(), inv.getRole()));
                    List<EmployeeDTO> companyEmployees = CompanyDAO.getInstance().getCompanyEmployees(manager.getCompanyId());
                    manager.getCompanyDTO().setEmployee(companyEmployees);
                    RedisDAOManager.getInstance().markChanged(s);
                    return Response.ok().entity(new Gson().toJson(companyEmployees)).build();
                }
            }
            return ERROR_BAD_REQUEST;
        }
    }

    @PUT
    @Path("name")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setCompanyName(@HeaderParam("sessionid") String sessionId, String companyName) {
        log.info("change company name");
        Session s = SessionsPool.find(sessionId);
        if (s == null || !s.getUser().getCompanyDTO().haveBoss(s.getUser().getLogin())) {
            return ERROR_FORBIDDEN;
        } else {
            companyName = companyName.replace("<", "&#60;").replace(">", "&#62;");
            if (companyName.length()==0){
                companyName = "Компанія без назви";
            }
            UserDTO user = s.getUser();
            if (CompanyDAO.getInstance().setCompanyName(user, companyName)){
                user.getCompanyDTO().setCompanyName(companyName);
                user.getCompanyDTO().getEmployee().forEach(e -> RedisDAOManager.getInstance().markChanged(e.getLogin()));
                return Response.ok(companyName).build();
            } else {
                return ERROR_BAD_REQUEST;
            }
        }
    }

    @POST
    @Path("/search/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchCompany(@HeaderParam("sessionid") String sessionId, @PathParam("id") long id, String filterJson) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<ResourceDTO> list;
            try {
                list = CompanyHelper.searchInCompany(s,id);
            } catch (ServerException e) {
                return Response.status(e.getCode()).build();
            }
            return Response.ok().entity(new Gson().toJson(list)).build();
        }
    }

    @DELETE
    @Path("/return/{login}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response returnUserDocuments(@HeaderParam("sessionid") String sessionId, @PathParam("login") String login){
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(login==null){
                return ERROR_BAD_REQUEST;
            }
            UserDTO manager = s.getUser();
            Long companyId = manager.getCompanyId();
            if(companyId ==null || !CompanyDAO.getInstance().getAdmins(companyId).contains(manager.getLogin())){
                return ERROR_LOCKED;
            }
            if(CompanyDAO.getInstance().returnUserDocuments(companyId,login)){
                manager.getCompanyDTO().getEmployee().forEach(e -> RedisDAOManager.getInstance().markChanged(e.getLogin()));
                return Response.ok().build();
            } else {
                return ERROR_SERVER;
            }

        }
    }
}
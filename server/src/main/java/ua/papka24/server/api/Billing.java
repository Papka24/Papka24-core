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
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.ResourceMiniDTO;
import ua.papka24.server.db.dto.billing.UploadTrend;
import ua.papka24.server.security.SecurityAttributes;
import ua.papka24.server.api.helper.BillingHelper;
import ua.papka24.server.db.dto.billing.BillingResponseDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.utils.datetime.DateTimeUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Path("billing")
public class Billing extends REST {

    private static final Logger log = LoggerFactory.getLogger(Billing.class);
    public static final Gson gson = new Gson();
    private static Type billingResponseDTOListType = new TypeToken<List<BillingResponseDTO>>() {}.getType();
    private static Type preferEgrpouType = new TypeToken<Map<String, Long>>() {}.getType();
    private static Type resourceMiniDtoType = new TypeToken<List<ResourceMiniDTO>>() {}.getType();
    private static Type uploadTrendsDtoType = new TypeToken<List<UploadTrend>>() {}.getType();
    private static Type getUserCompanyBillingType = new TypeToken<Map<Long, Long>>() {}.getType();

    @GET
    @Path("/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getList(@HeaderParam("sessionid") String sessionId,
                                  @PathParam("type") String type,
                                  @QueryParam("from") long from,
                                  @QueryParam("to") long to) {
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(to<from){
                return ERROR_BAD_REQUEST;
            }
            try {
                List<BillingResponseDTO> data;
                BillingHelper bh = BillingHelper.getInstance();
                if ("egrpou".equals(type)) {
                    data = bh.getByEgrpou(from, to);
                } else if ("login".equals(type)) {
                    data = bh.getByLogin(from, to);
                } else {
                    return ERROR_BAD_REQUEST;
                }
                if (data == null) {
                    return ERROR_SERVER;
                }
                String json = gson.toJson(data, billingResponseDTOListType);
                return Response.ok().entity(json).build();
            }catch (Exception ex){
                log.error("error, ex");
            }
        }
        return ERROR_SERVER;
    }

    @GET
    @Path("/preferegrpou")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPreferEgrpou(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            Map<String, Long> preferEgrpou = BillingHelper.getInstance().getPreferEgrpou();
            return Response.ok().entity(gson.toJson(preferEgrpou, preferEgrpouType)).build();
        }
    }

    @GET
    @Path("/detail/{id}/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDetailInfo(@HeaderParam("sessionid") String sessionId,
                                  @PathParam("id") String id,
                                  @QueryParam("from") long from,
                                  @QueryParam("to") long to,
                                  @QueryParam("type") @DefaultValue("c") String type) {
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            BillingHelper bh = BillingHelper.getInstance();
            if("null".equals(id)){
                List<ResourceMiniDTO> collect = bh.getNullDetailsByEGRPOU(from, to);
                return Response.ok().entity(gson.toJson(collect, resourceMiniDtoType)).build();
            }else {
                if (NumberUtils.isCreatable(id)) {
                    Long parametr = Long.valueOf(id);
                    List<ResourceMiniDTO> collect;
                    if (parametr<0) {
                        collect = bh.getResourceDetailsByInn(-parametr, from, to);
                    } else {
                        collect = bh.getResourceDetailsByEGRPOU(parametr, from, to);
                    }
                    return Response.ok().entity(gson.toJson(collect, resourceMiniDtoType)).build();
                } else if (id.contains("@")) {
                    List<ResourceMiniDTO> collect = bh.getResourceDetailsByLogin(id, from, to, type);
                    return Response.ok().entity(gson.toJson(collect, resourceMiniDtoType)).build();
                } else {
                    return ERROR_BAD_REQUEST;
                }
            }
        }
    }

    @PUT
    @Path("/{login}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setPreferEgrpou(@HeaderParam("sessionid") String sessionId, @PathParam("login") String login, String egrpou){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            BillingHelper.getInstance().setPreferEgrpou(login, Long.valueOf(egrpou));
        }
        return Response.ok().build();
    }

    @GET
    @Path("/nonprivattrend")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUploadTrend(@HeaderParam("sessionid") String sessionId,
                                   @QueryParam("from") Long from,
                                   @QueryParam("to") Long to,
                                   @QueryParam("bylogin") @DefaultValue("false") boolean byLogin){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(to==null){
                to = System.currentTimeMillis();
            }
            if(from==null){
                from = DateTimeUtils.addNDays(to, -30);
            }
            List<UploadTrend> trend = Collections.emptyList();
            if(byLogin) {
                trend = BillingHelper.getInstance().getUploadLoginsTrends(from, to);
            }
            return Response.ok().entity(gson.toJson(trend, uploadTrendsDtoType)).build();
        }
    }

    @PUT
    @Path("catalogpb")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveLoginToCatalogPb(@HeaderParam("sessionid") String sessionId, String login){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            BillingHelper.getInstance().saveLoginToCatalogPb(login, s.getUser().getLogin());
        }
        return Response.ok().build();
    }

    @GET
    @Path("/top/{n}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopNCompany(@HeaderParam("sessionid") String sessionId,
                                   @PathParam("n") long number,
                                   @QueryParam("from") long from,
                                   @QueryParam("to") long to){
        Session s = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN, SecurityAttributes.READ_BILLING);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            LocalDate f = new Date(from).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate t = new Date(to).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            Period between = Period.between(f,t);
            if(between.getMonths()>1){
                return ERROR_BAD_REQUEST;
            }
            List<BillingResponseDTO> topNCompanies = BillingHelper.getInstance().getTopNCompanies(number, from, to);
            return Response.ok().entity(gson.toJson(topNCompanies, billingResponseDTOListType)).build();
        }
    }

    @GET
    @Path("/user")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getUserBilling(@HeaderParam("sessionid") String sessionId, @QueryParam("from") Long from, @QueryParam("to") Long to){
        Session session = SessionsPool.find(sessionId);
        if(session == null){
            return ERROR_FORBIDDEN;
        }
        try{
            if(to == null){
                to = System.currentTimeMillis();
            }
            if(from == null){
                from = DateTimeUtils.getStartOfMonth();
            }
            long count = BillingHelper.getInstance().getUserBilling(session.getUser().getLogin(), from, to);
            return Response.ok().entity(""+count).build();
        }catch (Exception ex){
            log.error("error", ex);
        }
        return ERROR_SERVER;
    }

    @GET
    @Path("/userCompany")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserCompanyBilling(@HeaderParam("sessionid") String sessionId, @QueryParam("from") Long from, @QueryParam("to") Long to){
        Session session = SessionsPool.find(sessionId);
        if(session == null){
            return ERROR_FORBIDDEN;
        }
        try{
            if(to == null){
                to = System.currentTimeMillis();
            }
            if(from == null){
                from = DateTimeUtils.getStartOfMonth();
            }
            Map<Long, Long> count = BillingHelper.getInstance().getUserCompanyBilling(session.getUser().getLogin(), from, to);
            return Response.ok().entity(gson.toJson(count, getUserCompanyBillingType)).build();
        }catch (Exception ex){
            log.error("error", ex);
        }
        return ERROR_SERVER;
    }
}

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
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.DTO.ResourceStatisticRequestDTO;
import ua.papka24.server.api.DTO.ResourceStatisticResponseDTO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.security.SecurityAttributes;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.utils.datetime.StringToDateParser;
import ua.papka24.server.utils.json.DateDeserializer;

import javax.annotation.PostConstruct;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.util.Date;


@Path("statistic")
public class Statistic extends REST {

    private static final Logger log = LoggerFactory.getLogger(Statistic.class);
    private Gson gson;

    @PostConstruct
    private void init(){
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        gson = builder.create();
    }

    @POST
    @Path("/doc")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocInfo(@HeaderParam("sessionid") String sessionId, String jsonRequest){
       return process(sessionId, jsonRequest);
    }

    private Response process(String sessionId, ResourceStatisticRequestDTO request){
        try {
            Session session = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
            if(session == null){
                return ERROR_FORBIDDEN;
            }
            log.info("doc statistic request:{}", request);
            ResourceStatisticResponseDTO resStatistic = runStatistic(request);
            return Response.ok(new Gson().toJson(resStatistic)).build();
        }catch (JsonParseException jpe){
            log.error("error parse",jpe);
            return ERROR_BAD_REQUEST;
        }
    }

    private Response process(String sessionId, String jsonRequest){
        Session session = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if(session == null){
            return ERROR_FORBIDDEN;
        }
        ResourceStatisticRequestDTO request;
        if(jsonRequest==null || jsonRequest.isEmpty()){
            return ERROR_BAD_REQUEST;
        }
        if(gson==null) {
            init();
        }
        try {
            request = gson.fromJson(jsonRequest, ResourceStatisticRequestDTO.class);
            return process(sessionId, request);
        }catch (JsonParseException jpe){
            log.error("error parse",jpe);
            return ERROR_BAD_REQUEST;
        }
    }

    private ResourceStatisticResponseDTO runStatistic(ResourceStatisticRequestDTO request) throws JsonParseException{
        return ResourceDAO.getInstance().getResourceStatistic(request);
    }

    @GET
    @Path("/doc/{dateFrom}/{dateTo}/{deleted}")
    @Produces(MediaType.TEXT_HTML)
    public String getInfo(
            @HeaderParam("sessionid") String sessionId,
            @PathParam("dateFrom") String dateFrom,
            @PathParam("dateTo") String dateTo,
            @PathParam("deleted") boolean deleted) {
        return getInfo(sessionId, dateFrom,dateTo,null,deleted);
    }

    @GET
    @Path("/doc/{dateFrom}/{dateTo}")
    @Produces(MediaType.TEXT_HTML)
    public String getInfo(
            @HeaderParam("sessionid") String sessionId,
            @PathParam("dateFrom") String dateFrom,
            @PathParam("dateTo") String dateTo) {
        return getInfo(sessionId, dateFrom,dateTo,null,true);
    }

    @GET
    @Path("/doc/{dateFrom}/{dateTo}/{deleted}/{email}")
    @Produces(MediaType.TEXT_HTML)
    public String getInfo(
            @HeaderParam("sessionid") String sessionId,
            @PathParam("dateFrom") String dateFrom,
            @PathParam("dateTo") String dateTo,
            @PathParam("email") String email,
            @PathParam("deleted") boolean deleted) {
        Session session = SessionsPool.findWithRight(sessionId, SecurityAttributes.READ_ADMIN, SecurityAttributes.WRITE_ADMIN);
        if(session == null){
            return "FORBIDDEN";
        }
        ResourceStatisticRequestDTO rsr = new ResourceStatisticRequestDTO();
        try {
            rsr.setDateFrom(StringToDateParser.getDate(dateFrom));
            rsr.setDateTo(StringToDateParser.getDate(dateTo));
        } catch (ParseException e) {
            log.error("parse date error");
            return "incorrect date format";
        }
        rsr.setEmail(email);
        rsr.setDeleted(deleted);
        ResourceStatisticResponseDTO response;
        try {
            response = runStatistic(rsr);
        }catch (Exception jpe){
            log.error("server error",jpe);
            return "server error";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>"+
                "<html lang=\"en\">"+
                "<head>"+
                    "<meta charset=\"utf-8\">"+
                    "<title>Статистика Папка24</title>"+
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"+
                    "<meta name=\"description\" content=\"Моя папка\">"+
                "<style type=\"text/css\">" +
                            ".center{" +
                            "text-align: center;" +
                            "position: absolute;" +
                            "margin: auto;" +
                            "left: 0;" +
                            "right: 0;" +
                            "top:0;" +
                            "bottom: 0;" +
                            "height: 20em;" +
                        "}" +
                        "</style>"+
                "</head></head>" +
                "<body>");
        sb.append("<table style=\"border: 0px solid black; border-collapse: collapse;\">");
        sb.append("<tr class=\"vertical-align:middle !important; height:60px;\">");
        sb.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Загруженные</th>");
        sb.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Расшаренные</th>");
        sb.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Подписанные</th>");
        sb.append("</tr>");
        sb.append("<tr class=\"vertical-align:middle !important; height:60px;\">");
        sb.append("<td style=\"vertical-align:middle; height:40px; " +
                "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\">").append(response.upload).append("</td>");
        sb.append("<td style=\"vertical-align:middle; height:40px; " +
                "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\">").append(response.shared).append("</td>");
        sb.append("<td style=\"vertical-align:middle; height:40px; " +
                "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\">").append(response.signed).append("</td>");
        sb.append("</tr>");
        sb.append("</table>");
        sb.append("</body>");
        return sb.toString();
    }
}
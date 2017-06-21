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

import ua.papka24.server.Main;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.utils.logger.Event;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantLock;

@javax.ws.rs.Path("report")
public class Report extends REST {

    private static final ReentrantLock lock = new ReentrantLock();

    @POST
    @javax.ws.rs.Path("/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response report(@HeaderParam("sessionid") String sessionId, @PathParam("type") String type, String data) {

        Session s = SessionsPool.find(sessionId);
        if (s != null) {
            if ("crash".equalsIgnoreCase(type)) {
                try {
                    Path p = Paths.get(Main.property.getProperty("crashLogPath", "/var/log/papka24/crash/"));
                    Path path = null;
                    if (!Files.exists(p)) {
                        p = Files.createDirectories(p);
                    }
                    if (Files.isDirectory(p) && Files.isWritable(p)) {
                        Charset charset = Charset.forName("UTF-8");
                        String date = LocalDate.now().toString();
                        path = Paths.get(p.toString(), date + s.getUser().getLogin());
                        if (!Files.exists(path)) {
                            lock.lock();
                            try {
                                if(!Files.exists(path)){
                                    path = Files.createFile(path);
                                    try (BufferedWriter writer = Files.newBufferedWriter(path, charset)) {
                                        writer.write(data, 0, data.length());
                                    }
                                }
                            }finally {
                                lock.unlock();
                            }
                        }
                    } else {
                        Main.log.warn("error check isDirectory && isWritable: {}", p);
                    }
                    if (path != null) {
                        Main.log.error("Report {} from {}: {}", type, s.getUser().getLogin(), path.toAbsolutePath());
                        Main.log.info("Report {} from {}: {}", type, s.getUser().getLogin(), path.toAbsolutePath(), Event.REPORT);
                    }
                } catch (Exception ex) {
                    Main.log.error("cannot save crash report to file", ex);
                }
            } else {
                if (!"signError".equals(type) && !"cleanup".equals(type) &&  data.length()> 1000){
                    data = data.substring(0, 1000) + "...";
                } else if (data.length()> 1000000) {
                    data = data.substring(0, 1000000) + "...";
                }
                Main.log.error("Report {} from {}: {}", type, s.getUser().getLogin(), data);
                Main.log.info("Report {} from {}: {}", type, s.getUser().getLogin(), data, Event.REPORT);
            }
        }
        return Response.ok().build();
    }
}

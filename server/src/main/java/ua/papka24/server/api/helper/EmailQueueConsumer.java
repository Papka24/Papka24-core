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

package ua.papka24.server.api.helper;

import com.sun.mail.smtp.SMTPTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.EmailDTO;
import ua.papka24.server.db.redis.email.CustomPriorityQuery;
import ua.papka24.server.utils.logger.Event;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Class for send emails.
 */
public class EmailQueueConsumer implements Runnable {
    public static final Logger log = LoggerFactory.getLogger("EmailQueue");
    private static final String contentType = "text/html; charset=utf-8";
    private Properties prop;
    private CustomPriorityQuery queue;
    private final String from;
    private boolean emailEnabled = true;
    private final boolean serviceSendEnabled;
    private final boolean startTlsRequired;

    public EmailQueueConsumer(CustomPriorityQuery queue, String emailServer, int emailPort, String emailUser, String emailPassword) {
        this.queue = queue;
        this.from = "webmaster@" + Main.DOMAIN;
        if(emailServer!=null && !emailServer.trim().isEmpty()) {
            this.prop = createEmailServerProp(emailServer, emailUser, emailPassword, emailPort);
        }else{
            emailEnabled = false;
        }
        serviceSendEnabled = Main.property.getProperty("emailServer.send.enabled", "true").equals("true");
        startTlsRequired = Main.property.getProperty("emailServer.startTLS").equalsIgnoreCase("true");
    }

    private Properties createEmailServerProp(String emailServer, String emailUser, String emailPassword, int emailPort) {
        prop = new Properties();
        boolean auth = (emailUser != null && emailPassword != null);
        if(emailUser!=null){
            prop.setProperty("email.user",emailUser);
        }
        if(emailPassword!=null) {
            prop.setProperty("email.password", emailPassword);
        }
        prop.setProperty("mail.smtp.host", emailServer);
        prop.put("mail.smtp.socketFactory.port", emailPort);
        prop.put("mail.smtp.auth", auth);
        prop.put("mail.smtp.port", emailPort);
        prop.put("mail.smtp.starttls.enable", startTlsRequired);
        return prop;
    }

    private void sendEmails(){
        try {
            Session session = Session.getInstance(prop, null);
            EmailDTO emailDTO = null;
            if(!queue.isEmpty()) {
                SMTPTransport transport = (SMTPTransport) session.getTransport("smtp");
                if(startTlsRequired) {
                    transport.setRequireStartTLS(true);
                    transport.setStartTLS(true);
                }
                transport.connect(prop.getProperty("mail.smtp.host"), prop.getProperty("email.user"), prop.getProperty("email.password"));
                try {
                    while (transport.isConnected() && (emailDTO = queue.poll()) != null) {
                        MimeMessage msg = new MimeMessage(session);
                        msg.setHeader("Content-Type", "text/plain; charset=UTF-8");
                        if (Main.DOMAIN.equals("papka24.com.ua")) {
                            msg.setFrom(new InternetAddress(from, "Папка24", "UTF-8"));
                        } else {
                            msg.setFrom(new InternetAddress(from, "Papka24", "UTF-8"));
                        }
                        msg.setRecipients(Message.RecipientType.TO,
                                InternetAddress.parse(emailDTO.getTo(), false));
                        msg.setSubject(emailDTO.getSubject(), "UTF-8");
                        if (emailDTO.getDataSource() != null) {
                            MimeMultipart multipart = new MimeMultipart("related");
                            BodyPart messageBodyPart = new MimeBodyPart();
                            if (emailDTO.getHtmlMessage() != null) {
                                messageBodyPart.setContent(emailDTO.getHtmlMessage(), contentType);
                                multipart.addBodyPart(messageBodyPart);
                                messageBodyPart = new MimeBodyPart();
                                messageBodyPart.setDataHandler(new DataHandler(emailDTO.getDataSource()));
                                messageBodyPart.setHeader("Content-ID", "<image>");
                                multipart.addBodyPart(messageBodyPart);
                                msg.setContent(multipart);
                            }
                        } else {
                            if (emailDTO.getHtmlMessage() != null) {
                                msg.setContent(emailDTO.getHtmlMessage(), contentType);
                            } else {
                                msg.setText(emailDTO.getMessage());
                            }
                        }
                        msg.setSentDate(new Date());
                        try {
                            transport.sendMessage(msg, msg.getAllRecipients());
                            String lastServerResponse = transport.getLastServerResponse();
                            log.info("Send EmailDTO by user {} to {} with subject {} and html template:{} (share template:{}) Server response:{}", emailDTO.getUser(), emailDTO.getTo(), emailDTO.getSubject(), emailDTO.getTepmplate(), emailDTO.getShareTemplate(), lastServerResponse, Event.SEND_EMAIL);
                            queue.process(emailDTO);
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (Exception ex) {
                                log.warn("nothing to do");
                            }
                        } catch (MessagingException mex) {
                            queue.process(emailDTO);
                            log.error("failed to send emailDTO", mex);
                            if (queue != null) {
                                queue.add(emailDTO);
                            }
                        }
                    }
                }catch(Exception ex){
                    log.error("filled to send email:{}", emailDTO);
                    throw ex;
                }finally {
                    try {
                        if(transport.isConnected()) {
                            transport.close();
                        }
                    }catch (MessagingException mex){
                        log.error("failed close smtp transport",mex);
                    }
                }

            }
        }catch(Exception ex){
            log.error("fail to send email",ex);

        }
    }

    @Override
    public void run() {
        while(emailEnabled && this.serviceSendEnabled) {
            try{
                if (!queue.isEmpty()) {
                    sendEmails();
                }
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception e) {
                log.error("Error start queue", e);
            }
        }
    }
}
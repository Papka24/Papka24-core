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

package ua.papka24.server.utils.freemarker;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.ShareTemplate;
import ua.papka24.server.db.dto.AnalyticsInfoDTO;
import ua.papka24.server.db.dto.EmployeeDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.service.events.event.EmailEvent;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TemplateManager {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TemplateManager.class);
    private Configuration cfg;

    private TemplateManager(){
        cfg = new Configuration(Configuration.VERSION_2_3_23);
        cfg.setDefaultEncoding("UTF-8");
        ClassTemplateLoader ctl = new ClassTemplateLoader(this.getClass(), "/template");
        String mailTemplatePath = Main.property.getProperty("mail_template_path");
        if(mailTemplatePath == null){
            mailTemplatePath = Main.CDNPath + "/template/";
        }
        FileTemplateLoader ftl = null;
        try {
            Path templateDirectory = Paths.get(mailTemplatePath);
            if (Files.exists(templateDirectory)) {
                ftl = new FileTemplateLoader(templateDirectory.toFile());
            }
        }catch (Exception ex){
            log.warn("cannot init template directory loader : {}", ex.getMessage());
        }
        TemplateLoader tl[];
        if(ftl !=null){
            tl = new TemplateLoader[]{ctl, ftl};
        }else{
            tl = new TemplateLoader[]{ctl};
        }
        MultiTemplateLoader mtl = new MultiTemplateLoader(tl);
        cfg.setTemplateLoader(mtl);
        cfg.setLogTemplateExceptions(false);
    }

    private static class Singleton {
        private static final TemplateManager HOLDER_INSTANCE = new TemplateManager();
    }

    public static TemplateManager getInstance() {
        return TemplateManager.Singleton.HOLDER_INSTANCE;
    }


    private String processTemplate(Template template, Map<String,Object> prop) throws IOException, TemplateException {
        StringWriter out = new StringWriter();
        template.process(prop, out);
        String result = out.toString();
        out.close();
        return result;
    }

    public String getGroupInviteEmail(String email) throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/group/invite_response.ftl");
        Map<String, Object> prop = new HashMap<>();
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("email",email);
        return processTemplate(template,prop);
    }

    public String getInviteEmail(String name, String secret) throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/invite.ftl");
        Map<String, Object> prop = new HashMap<>();
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("name",name);
        prop.put("secret", secret);
        return processTemplate(template,prop);
    }

    public String getGroupInviteEmail(UserDTO user,  boolean register, String secret, long role) throws IOException, TemplateException {
        Template groupInvite;
        if(role==EmployeeDTO.ROLE_ADMIN){
            if(register){
                groupInvite = cfg.getTemplate("/email/group/invite_admin.ftl");
            }else{
                groupInvite = cfg.getTemplate("/email/group/invite_admin_new.ftl");
            }
        }else if(role == EmployeeDTO.ROLE_WORKER) {
            if (register) {
                groupInvite = cfg.getTemplate("/email/group/invite_member.ftl");
            } else {
                groupInvite = cfg.getTemplate("/email/group/invite_member_new.ftl");
            }
        }else{
            return null;
        }

        Map<String,Object> prop = new HashMap<>();
        prop.put("authorName",user.getFullName());
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("secret", secret);
        prop.put("groupName",user.getCompanyDTO().getName());
        return processTemplate(groupInvite,prop);
    }

    public String getShareEmail(String authorName, String docName, String comment, long docId, boolean sendInvite, String secret, String encryptedEmail, String encryptedId) throws IOException, TemplateException {
        Template shareTemplate = cfg.getTemplate("/email/share.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("authorName",authorName);
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("docName",docName);
        prop.put("comment",comment);
        prop.put("sendInvite",sendInvite);
        prop.put("secret",secret);
        prop.put("docId",docId);
        prop.put("encryptedEmail",encryptedEmail);
        prop.put("encryptedId",encryptedId);
        return processTemplate(shareTemplate,prop);
    }

    public String getShareEmail(List<EmailEvent> shareEventsInfo, String encryptedEmail, String encryptedId) throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/share_mass.ftl");
        EmailEvent ee = shareEventsInfo.get(0);
        Map<String, Object> prop = new HashMap<>();
        prop.put("emailEvent",shareEventsInfo);
        prop.put("sendInvite", ee.isSendInvite());
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("secret",ee.getToEmail());
        prop.put("encryptedEmail",encryptedEmail);
        prop.put("encryptedId",encryptedId);
        return processTemplate(template, prop);
    }

    public String getSignEmail(String docName, long docId, String authorName, String encryptedEmail) throws IOException, TemplateException {
        Template shareTemplate = cfg.getTemplate("/email/sign.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("authorName",authorName);
        prop.put("docName",docName);
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("docId",docId);
        prop.put("encriptedEmail",encryptedEmail);
        return processTemplate(shareTemplate,prop);
    }

    public String getSignEmail(List<EmailEvent> emailEvents, String encryptedEmail, String encryptedId) throws IOException, TemplateException {
        Template shareTemplate = cfg.getTemplate("/email/sign_mass.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("emailEvent",emailEvents);
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("encriptedEmail",encryptedEmail);
        prop.put("encryptedId",encryptedId);
        return processTemplate(shareTemplate,prop);
    }

    public String getNews() throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/news/n20160714.ftl");
        return processTemplate(template,new HashMap<>());
    }

    public String getAnalyticsPage(List<AnalyticsInfoDTO> analyticsInfoList) throws IOException, TemplateException {
        Template template = cfg.getTemplate("analytics.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("infolist",analyticsInfoList);
        return processTemplate(template,prop);
    }

    public String getDisableShareEmail(String authorName, String docName, boolean unknown) throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/disableShare.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("mainDomain",Main.DOMAIN);
        prop.put("authorName",authorName);
        prop.put("docName",docName);
        prop.put("unknown",unknown);
        return processTemplate(template,prop);
    }

    public String getNewChatMessage(String userLogin, Map<String, Long> resourcesInfo, String unsubscribeId) throws IOException, TemplateException {
        Template template = cfg.getTemplate("/email/spam/new_chat_message.ftl");
        Map<String,Object> prop = new HashMap<>();
        prop.put("userLogin", userLogin);
        prop.put("singleDocument", resourcesInfo.values().size()==1);
        prop.put("mainDomain", Main.DOMAIN);
        prop.put("resourcesInfo", resourcesInfo);
        prop.put("unsubscribeId",unsubscribeId);
        return processTemplate(template,prop);
    }

    public String getSharingTemplate(long docId, ShareTemplate sharedTemplate) throws IOException, TemplateException {
        String lang = sharedTemplate.getLang();
        if(lang == null){
            lang = ShareTemplate.DEFAULT_LANG;
        }
        String templateName = "/email/tplt/"+lang+"/"+sharedTemplate.getName() + ".ftl";
        Template template = cfg.getTemplate(templateName);
        Map<String,Object> prop = new HashMap<>();
        prop.put("mainDomain", Main.DOMAIN);
        prop.put("docId",docId);
        prop.putAll(sharedTemplate.getParams());
        return processTemplate(template,prop);
    }
}

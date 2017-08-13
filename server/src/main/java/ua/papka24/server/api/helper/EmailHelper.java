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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.EmailDTO;
import ua.papka24.server.api.DTO.EmailInfo;
import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.dao.SpamDAO;
import ua.papka24.server.db.dto.ShareDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.service.events.event.EmailEvent;
import ua.papka24.server.utils.freemarker.TemplateManager;
import ua.papka24.server.api.DTO.ShareTemplate;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


//TODO свернуть все в шаблоны
public class EmailHelper {

    private static final Logger log = LoggerFactory.getLogger(EmailHelper.class);

    public enum Type{
        SPAM1               (0b00000000_00000000_00000000_00000001, "not sign any resource"),
        SIGN                (0b00000000_00000000_00000000_00000010, "resource was signed"),
        SPAM2               (0b00000000_00000000_00000000_00000100, "sign but not share"),
        SPAM5               (0b00000000_00000000_00000000_00001000, "remember - not register user has some documents"),
        SPAM34              (0b00000000_00000000_00000000_00010000, "remember - your shared documents was not seen or signed"),
        SHARED              (0b00000000_00000000_00000000_00100000, "resource was shared"),
        NEW_CHAT_MESSAGE    (0b00000000_00000000_00000000_01000000, "new chat message");

        Type(int type, String desc){
            this.type = type;
            this.desc = desc;
        }

        public int type;
        public String desc;
    }

    private static Pattern email_pattern = Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
    private static IvParameterSpec iv = new IvParameterSpec(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
    private static SecretKeySpec skeySpec = new SecretKeySpec(Main.property.getProperty("secret_key_spec", "SecretKeyForEmai").getBytes(), "AES");

    public static boolean validate(final String email) {
        return email_pattern.matcher(email).matches();

    }

    public static String getEncryptedId(String email) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(email.getBytes()));
        } catch (IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            Main.log.error("EmailHelper can't encrypt data", e);
        }
        return "";
    }

    public static String getDecryptedId(String encrypted) {
        try {
            byte[] data = Base64.getUrlDecoder().decode(encrypted);
            if (data.length % 16 != 0) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException e) {
            Main.log.error("EmailHelper can't decrypt data", e);
        }
        return "";
    }

    private static Random rnd = new Random();

    public static String getEncryptedCompanyInvite(GroupInviteDTO groupInvite) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            String result = groupInvite.getEmail() + "@" + groupInvite.getInitiator() + "@" + groupInvite.getCompanyId() + "@" + groupInvite.getRole();
            byte[] resBytes = result.getBytes(StandardCharsets.UTF_8);
            byte[] rndBytes = new byte[4];

            byte control = 0x0f;
            for (byte b : resBytes) {
                control %= b;
            }
            rnd.nextBytes(rndBytes);
            byte[] f = ArrayUtils.addAll(resBytes, control);
            f = ArrayUtils.addAll(f, rndBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(f));
        } catch (IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            Main.log.error("company invite can't encrypted", e);
        }
        return "";
    }

    public static GroupInviteDTO getDecryptedCompanyInvite(String encrypted) {
        GroupInviteDTO res = null;
        try {
            byte[] data = Base64.getUrlDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] decrypt = cipher.doFinal(data);
            byte control = 0x0f;
            for (int i = 0; i > decrypt.length - 5; i++) {
                control %= decrypt[i];
            }
            if (control != decrypt[decrypt.length - 5]) {
                return null;
            } else {
                String[] decoder = (new String(ArrayUtils.subarray(decrypt, 0, decrypt.length - 5), StandardCharsets.UTF_8)).split("@");
                if (decoder.length == 6) {
                    res = new GroupInviteDTO(decoder[0] + "@" + decoder[1], Long.parseLong(decoder[4]), Long.parseLong(decoder[5]), decoder[2] + "@" + decoder[3]);
                }
                return res;

            }
        } catch (BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException e) {
            Main.log.error("EmailHelper can't decrypt company invite", e);
            return null;
        }
    }

    public static String getEncryptedEmailInfo(EmailInfo emailInfo) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            String result = emailInfo.getLogin() + "@" + emailInfo.getType() + "@" + emailInfo.getInfo().replaceAll("@", "");
            byte[] resBytes = result.getBytes(StandardCharsets.UTF_8);
            byte[] rndBytes = new byte[4];

            byte control = 0x0f;
            for (byte b : resBytes) {
                control %= b;
            }
            rnd.nextBytes(rndBytes);
            byte[] f = ArrayUtils.addAll(resBytes, control);
            f = ArrayUtils.addAll(f, rndBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(f));
        } catch (IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            Main.log.error("User invite can't encrypted", e);
        }
        return "";
    }

    public static EmailInfo getDecryptedEmailInfo(String encrypted) {
        EmailInfo res = null;
        try {
            byte[] data = Base64.getUrlDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] decrypt = cipher.doFinal(data);
            byte control = 0x0f;
            for (int i = 0; i > decrypt.length - 3; i++) {
                control %= decrypt[i];
            }
            if (control != decrypt[decrypt.length - 5]) {
                return null;
            } else {
                String[] decoder = (new String(ArrayUtils.subarray(decrypt, 0, decrypt.length - 5), StandardCharsets.UTF_8)).split("@");
                if (decoder.length == 4) {
                    res = new EmailInfo(decoder[0] + "@" + decoder[1], Integer.valueOf(decoder[2]));
                }
                return res;

            }
        } catch (BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException e) {
            Main.log.error("EmailHelper can't decrypt user invite", e);
            return null;
        }
    }

    public static void sendShareEmail(String user, String toEmail, long docId, String docName, String authorName, boolean sendInvite, String comment, ShareTemplate template) {
        try {
            EmailInfo emailInfo = new EmailInfo(toEmail, Type.SHARED.type);
            String encryptedEmail = getEncryptedEmailInfo(emailInfo);
            String encryptedId = getEncryptedId(toEmail);
            String htmlMessage;
            EmailDTO.Template emailTemplate;
            String authName = TextUtils.isEmpty(authorName) ? user : authorName;
            if(template==null){
                htmlMessage = TemplateManager.getInstance().getShareEmail(authName, docName, comment, docId, sendInvite, toEmail, encryptedEmail, encryptedId);
                emailTemplate = EmailDTO.Template.SHARE;
            }else{
                htmlMessage = TemplateManager.getInstance().getSharingTemplate(docId, template);
                if(!TextUtils.isEmpty(template.getParams().get("subject"))){
                    docName = template.getParams().get("subject");
                }
                emailTemplate = EmailDTO.Template.TEMPLATE_SHARE;
            }
            if (htmlMessage != null) {
                Main.emailQueue.add(new EmailDTO(user, toEmail, docName, htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, emailTemplate, template));
            }
        } catch (Exception e) {
            log.error("error send email", e);
        }
    }

    public static void sendSignEmail(String user, String toEmail, String docName, long docId, String authorName) {
        try {
            EmailInfo emailInfo = new EmailInfo(toEmail, Type.SIGN.type);
            String encryptedEmail = getEncryptedEmailInfo(emailInfo);
            String htmlMessage = TemplateManager.getInstance().getSignEmail(docName, docId, authorName, encryptedEmail);
            if (htmlMessage != null) {
                Main.emailQueue.add(new EmailDTO(user, toEmail, docName + " - Підписано", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.SIGN));
            }
        } catch (Exception e) {
            log.error("error send email", e);
        }
    }

    public static void sendInviteToGroupEmail(UserDTO manager, String email, boolean register, String secret, long role) {
        try {
            String htmlMessage = TemplateManager.getInstance().getGroupInviteEmail(manager, register, secret, role);
            if (htmlMessage != null) {
                Main.emailQueue.add(new EmailDTO(manager.getLogin(), email, manager.getFullName() + " додав Вас у корпорацію на Папка24", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.INVITE_TO_GROUP));
            }
        } catch (Exception e) {
            log.error("error send invite to group message", e);
        }
    }

    public static void sendAcceptInviteToGroupEmail(String email, String initiator) {
        try {
            String htmlMessage = TemplateManager.getInstance().getGroupInviteEmail(email);
            if (htmlMessage != null) {
                Main.emailQueue.add(new EmailDTO(email, initiator, email + " прийняв Ваше запрошення", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.ACCEPT_GROUP_INVITE));
            }
        } catch (Exception e) {
            log.error("error send invite to group message", e);
        }
    }

    public static void sendInviteEmail(String login, String name, String secret) {
        String message = "Вітаємо, " + name +
                "!\n\n" +
                "Останній крок, і Ви потрапите в чарівний світ без паперів :)\n\n Підтвердіть Ваш email, будь ласка, клікнувши на це посилання\n\n" +
                "https://" + Main.DOMAIN + "/accept#" + secret;

        StringBuilder messageHTML = new StringBuilder("<html><body>Вітаємо, ");
        messageHTML.append(name);
        messageHTML.append("!<br><br>");
        messageHTML.append("Останній крок, і Ви потрапите в чарівний світ без паперів :)<br><br> Підтвердіть Ваш email, будь ласка, клікнувши на це посилання<br><br>");
        messageHTML.append("<a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold' href='https://").append(Main.DOMAIN).append("/accept#").append(secret).append("'>Підтвердити</a>");
        messageHTML.append("</body><html>");
        Main.emailQueue.add(new EmailDTO(login, login, "Папка24. Підтвердження реєстрації", message, messageHTML.toString(), EmailDTO.Priority.HIGH, EmailDTO.Template.INVITE));
    }

    public static void sendResetPasswordEmail(String login, String name, String secret) {
        String message = "Вітаємо, " + name +
                "!\n\n" +
                "Сформували заявку на зміну пароля, якщо це були ви, натисніть на посилання\n\n" +
                "https://" + Main.DOMAIN + "/passreset#" + secret;

        StringBuilder messageHTML = new StringBuilder("<html><body>Вітаємо, ");
        messageHTML.append(name);
        messageHTML.append("!<br><br>");
        messageHTML.append("Сформували заявку на зміну пароля, якщо це були ви, натисніть на кнопку<br><br>");
        messageHTML.append("<a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold' href='https://").append(Main.DOMAIN).append("/passreset#").append(secret).append("'>Змінити пароль</a>");
        messageHTML.append("</body><html>");
        Main.emailQueue.add(new EmailDTO(login, login, "Папка24. Підтвердження зміни пароля", message, messageHTML.toString(), EmailDTO.Priority.HIGH, EmailDTO.Template.RESET_PASSWORD));
    }

    public static void sendDisableShareEmail(String user, String toEmail, long docId, String docName, String authorName, int status) {
        try {
            String messageHTML = TemplateManager.getInstance().getDisableShareEmail(authorName, docName, status == ShareDTO.STATUS_SEND_TO_UNKNOWN_USER);
            Main.emailQueue.add(new EmailDTO(user, toEmail, "Закриття доступу до " + docName, messageHTML, messageHTML, EmailDTO.Priority.LOW, EmailDTO.Template.DISABLE_SHARE));
        }catch (Exception ex){
            log.error("error send disable share email",ex);
        }
    }

    public static void sendSpam1(String user) {
        StringBuilder messageHTML = new StringBuilder("<html><body>");
        messageHTML.append("Доброго дня!<br><br>");
        messageHTML.append("Мене звати Дмитро Дубілет, я – керівник проекту Папка24.<br>");
        messageHTML.append("Дуже Вам дякую за реєстрацію!<br><br>");
        messageHTML.append("Ми бачимо, що Ви після реєстрації не підписали жодного документу.<br>");
        messageHTML.append("Можливо у Вас виникли якісь проблеми? Наша служба підтримки буде рада<br>Вам допомогти з будь-якими питаннями!<br><br>");
        messageHTML.append("Пишіть нам будь-які Ваші питання або побажання на <a href='mailto:info@papka24.com.ua'>info@papka24.com.ua</a><br>Дякую!<br><br>");
        messageHTML.append("--<br>");
        messageHTML.append("З повагою, Дмитро Дубілет<br>");
        messageHTML.append("Керівник проекту Папка24<br>");
        EmailInfo emailInfo = new EmailInfo(user, Type.SPAM1.type);
        String encryptedEmail = getEncryptedEmailInfo(emailInfo);
        messageHTML.append("<br><br><a style='color:#999' href='https://").append(Main.DOMAIN).append("/api/login/email/").append(encryptedEmail).append("'>Відписатись від листів</a>");
        messageHTML.append("</body></html>");
        Main.emailQueue.add(new EmailDTO(user, user, "Чи не виникло у Вас проблем с Папка24?", messageHTML.toString(), messageHTML.toString(), EmailDTO.Priority.LOW, EmailDTO.Template.SPAM1));
    }

    public static void sendSpam2(String user) {
        StringBuilder messageHTML = new StringBuilder("<html><body>");
        messageHTML.append("Доброго дня!<br><br>");
        messageHTML.append("Мене звати Дмитро Дубілет, я – керівник проекту Папка24.<br>");
        messageHTML.append("Дуже Вам дякую за реєстрацію!<br><br>");
        messageHTML.append("Ми бачимо, що Ви вдало підписали свій перший документ на Папка24. Ура!<br>");
        messageHTML.append("Але Ви ще не відправили жодного документу своїм контрагентам.!<br><br>");
        messageHTML.append("Можливо у Вас виникли якісь проблеми? Наша служба підтримки буде рада Вам допомогти з будь-якими питаннями!<br><br>");
        messageHTML.append("Пишіть нам будь-які Ваші питання або побажання на <a href='mailto:info@papka24.com.ua'>info@papka24.com.ua</a><br>Дякую!<br><br>");
        messageHTML.append("--<br>");
        messageHTML.append("З повагою, Дмитро Дубілет<br>");
        messageHTML.append("Керівник проекту Папка24<br>");
        EmailInfo emailInfo = new EmailInfo(user, Type.SPAM2.type);
        String encryptedEmail = getEncryptedEmailInfo(emailInfo);
        messageHTML.append("<br><br><a style='color:#999' href='https://").append(Main.DOMAIN).append("/api/login/email/").append(encryptedEmail).append("'>Відписатись від листів</a>");
        messageHTML.append("</body></html>");
        Main.emailQueue.add(new EmailDTO(user, user, "Вау! Вітаємо з першим підписаним документом! :)", messageHTML.toString(), messageHTML.toString(), EmailDTO.Priority.LOW, EmailDTO.Template.SPAM2));
    }

    public static void sendSpam5(String user, Map<String, List<SpamDAO.InfoHolder>> authorDocs) {
        StringBuilder messageHTML = new StringBuilder("<html><body>");
        messageHTML.append("Доброго дня!<br><br>");
        messageHTML.append("Нещодавна ");

        for (String author : authorDocs.keySet()) {
            messageHTML.append(author);
            List<SpamDAO.InfoHolder> ihList = authorDocs.get(author);
            if (ihList.size() == 1) {
                messageHTML.append(" відправив Вам документ ");
            } else {
                messageHTML.append(" відправив Вам документи:<br> ");
            }
            for (SpamDAO.InfoHolder ih : ihList) {
                messageHTML.append(" - <a href='https://").append(Main.DOMAIN).append("/doc/").append(ih.resourceId).append("'>").append(ih.desc).append("</a><br> ");
            }
            messageHTML.append("<br>");
        }
        messageHTML.append("<br>");
        messageHTML.append("Нагадуємо, що Ви можете зареєструватися у сервісі Папка24 за 1 хвилину прямо зараз.<br><br>");
        messageHTML.append("<a href='https://").append(Main.DOMAIN).append('/').append('\'').append(">Зареєструватись за 1 хвилину</a><br><br>");
        messageHTML.append("Можливо у Вас виникли якісь проблеми? Наша служба підтримки буде рада Вам допомогти з будь-якими питаннями!<br><br>");
        messageHTML.append("Пишіть нам будь-які Ваші питання або побажання на <a href='mailto:info@papka24.com.ua'>info@papka24.com.ua</a><br>Дякую!<br><br>");
        messageHTML.append("--<br>");
        messageHTML.append("З повагою, команда Папка24<br><br>");
        EmailInfo emailInfo = new EmailInfo(user, Type.SPAM5.type);
        String encryptedEmail = getEncryptedEmailInfo(emailInfo);
        messageHTML.append("<br><br><a style='color:#999' href='https://").append(Main.DOMAIN).append("/api/login/email/").append(encryptedEmail).append("'>Відписатись від листів</a>");
        messageHTML.append("</body></html>");
        Main.emailQueue.add(new EmailDTO(user, user, "Нагадуємо, Вам відправлено документ", messageHTML.toString(), messageHTML.toString(), EmailDTO.Priority.LOW, EmailDTO.Template.SPAM5));
    }

    public static void sendSpam34(String author, Map<String, List<SpamDAO.InfoHolder>> sharedUsersList) {
        StringBuilder messageHtml = new StringBuilder("<html><body>");
        messageHtml.append("Доброго дня!<br><br>");
        messageHtml.append("Раніше Ви завантажили на Папка24 документ(-и), які не були переглянуті або підписані Вашими контрагентами.<br><br>");
        //table
        messageHtml.append("<table style=\"border: 0px solid black; border-collapse: collapse;\">");
        messageHtml.append("<tr class=\"vertical-align:middle !important; height:60px;\">");
        messageHtml.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Контрагент</th>");
        messageHtml.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Документ</th>");
        messageHtml.append("<th style=\"text-align: left; padding: 5px 10px 5px 0; border: 0px solid black;\">Статус</th>");
        messageHtml.append("</tr>");

        for (String sharedUser : sharedUsersList.keySet()) {
            messageHtml = fillSharedUserBlock(messageHtml, sharedUser, sharedUsersList.get(sharedUser));
        }
        messageHtml.append("</table>");
        messageHtml.append("Наша служба підтримки буде рада допомогти Вам та Вашим контрагентам з будь-якими питаннями!<br><br>");
        messageHtml.append("Пишіть нам будь-які Ваші питання або побажання на <a href='mailto:info@papka24.com.ua'>info@papka24.com.ua</a><br>Дякую!<br><br>");
        messageHtml.append("--<br>");
        messageHtml.append("З повагою, команда Папка24<br><br>");
        EmailInfo emailInfo = new EmailInfo(author, Type.SPAM34.type);
        String encryptedEmail = getEncryptedEmailInfo(emailInfo);
        messageHtml.append("<br><br><a style='color:#999' href='https://").append(Main.DOMAIN).append("/api/login/email/").append(encryptedEmail).append("'>Відписатись від листів</a>");
        messageHtml.append("</body></html>");
        Main.emailQueue.add(new EmailDTO(author, author, "Статуси по документах Папка24", messageHtml.toString(), messageHtml.toString(), EmailDTO.Priority.LOW, EmailDTO.Template.SPAM34));
    }

    private static StringBuilder fillSharedUserBlock(StringBuilder sb, String sharedUser, List<SpamDAO.InfoHolder> documents) {
        boolean skip = false;
        for (SpamDAO.InfoHolder ih : documents) {
            sb.append("<tr class=\"vertical-align:middle !important; height:60px;\">");
            if (skip) {
                sb.append("<td style=\"vertical-align:middle; height:40px; " +
                        "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\"></td>");
            } else {
                sb.append("<td style=\"vertical-align:middle; height:40px;" +
                        "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\">");
                if (ih.sharedUserFullName != null) {
                    sb.append(ih.sharedUserFullName).append("<br>");
                }
                sb.append("<a href='mailto:").append(sharedUser).append("'>")
                        .append(sharedUser)
                        .append("</a></td>");
                skip = true;
            }
            sb.append("<td style=\"vertical-align:middle; height:40px; " +
                    "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\"><a href='https://").append(Main.DOMAIN).append("/doc/").append(ih.resourceId).append("'>").append(ih.desc).append("</a></td>");
            sb.append("<td style=\"vertical-align:middle; height:40px; " +
                    "text-align: left; border: 0px solid black; padding: 5px 10px 5px 0;\">").append(getTypeDescription(ih.type, ih.status)).append("</td>");
            sb.append("</tr>");
        }
        return sb;
    }

    private static String getTypeDescription(int type, int status) {
        switch (type) {
            case 3: {
                return "Ще не зареєструвався у сервісі Папка24";
            }
            case 4: {
                if (status == 1 || status == 11) {
                    return "Ще не переглядав документ";
                } else if (status == 2 || status == 12) {
                    return "Переглядав, але не підписав документ";
                }
                return "Ще не зареєструвався у сервісі Папка24";
            }
        }
        return "";
    }

    public static void sendMassShareEmail(String toEmail, List<EmailEvent> shareEventsInfo) {
        log.info("sendMassShareEmail : {} : {}",toEmail,shareEventsInfo);
        try {
            EmailInfo emailInfo = new EmailInfo(toEmail, Type.SHARED.type);
            String encryptedEmail = getEncryptedEmailInfo(emailInfo);
            String encryptedId = getEncryptedId(toEmail);
            shareEventsInfo.forEach(e->{
                if(TextUtils.isEmpty(e.getAuthorName())){
                    e.setAuthorName(e.getUser());
                }
            });
            String htmlMessage = TemplateManager.getInstance().getShareEmail(shareEventsInfo, encryptedEmail, encryptedId);
            if (htmlMessage != null) {
                String usersCollect = shareEventsInfo.stream().map(EmailEvent::getUser).collect(Collectors.joining(", "));
                Main.emailQueue.add(new EmailDTO(usersCollect, toEmail, "Документи, до яких вам надали спільний доступ", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.SHARE));
            }
        } catch (Exception e) {
            log.error("error send email", e);
        }
    }

    public static void sendMassSignEmail(String login, List<EmailEvent> emailEvents) {
        log.info("sendMassSignEmail : {} : {}",login,emailEvents);
        try {
            EmailInfo emailInfo = new EmailInfo(login, Type.SIGN.type);
            String encryptedEmail = getEncryptedEmailInfo(emailInfo);
            String encryptedId = getEncryptedId(login);
            String htmlMessage = TemplateManager.getInstance().getSignEmail(emailEvents, encryptedEmail, encryptedId);
            if (htmlMessage != null) {
                String usersCollect = emailEvents.stream().map(EmailEvent::getUser).collect(Collectors.joining(", "));
                Main.emailQueue.add(new EmailDTO(usersCollect, login, "Документи, що були підписані", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.SIGN));
            }
        } catch (Exception e) {
            log.error("error send email", e);
        }
    }

    public static void sendNewChatMessageEmail(String userLogin, Map<String, Long> resourcesInfo) {
        log.info("sendNewChatMessageEmail : {} : {}",userLogin, resourcesInfo);
        try{
            EmailInfo emailInfo = new EmailInfo(userLogin, Type.NEW_CHAT_MESSAGE.type);
            String unsubscribeId = getEncryptedEmailInfo(emailInfo);
            String htmlMessage = TemplateManager.getInstance().getNewChatMessage(userLogin, resourcesInfo, unsubscribeId);
            if (htmlMessage != null) {
                Main.emailQueue.add(new EmailDTO(userLogin, userLogin, "Нові повідомлення в обговореннях", htmlMessage, htmlMessage, EmailDTO.Priority.NORMAL, EmailDTO.Template.NEW_CHAT_MESSAGE));
            }
        }catch (Exception ex){
            log.error("error send email", ex);
        }
    }

    public static String parse(Integer spamMode){
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        final int[] i = {0};
        Arrays.stream(Type.values()).filter(type->(type.type & spamMode)>0).forEach(type->{
            if(i[0] >0) {
                sb.append(',');
            }
            sb.append(type.name());
            sb.append(':');
            sb.append(type.type);
            i[0]++;
        });
        sb.append(']');
        return sb.toString();
    }
}

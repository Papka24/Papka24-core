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

import com.mortennobel.imagescaling.ResampleOp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ua.papka24.server.db.dao.BillingDAO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.security.*;
import ua.papka24.server.Main;
import ua.papka24.server.api.helper.BillingHelper;
import ua.papka24.server.db.scylla.Analytics;
import ua.papka24.server.utils.logger.Event;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;


public class Upload implements HttpHandler {
    static private SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
    static private Tika detector = new Tika();
    private static final Logger log = LoggerFactory.getLogger("upload");
    private static final String forbiddenResponse = "Forbidden";

    @Override
    public void handle(HttpExchange t) throws IOException {
        boolean multipart = false;
        if (!t.getRequestHeaders().containsKey("sessionid")) {
            while (t.getRequestBody().read() != -1) {
            }
            t.sendResponseHeaders(403, forbiddenResponse.length());
            OutputStream os = t.getResponseBody();
            os.write(forbiddenResponse.getBytes());
            os.close();
        }
        Session session = null;
        String frontVersion = "API";
        if (t.getRequestHeaders().containsKey("sessionid")) {
            String sessionId = t.getRequestHeaders().get("sessionid").get(0);
            session = SessionsPool.find(sessionId);
            if (session == null) {
                while (t.getRequestBody().read() != -1) {
                }
                t.sendResponseHeaders(423, forbiddenResponse.length());
                OutputStream os = t.getResponseBody();
                os.write(forbiddenResponse.getBytes());
                os.close();
            } else {
                //заполнение mdc
                try {
                    MDC.remove("message");
                    MDC.remove("request");
                    MDC.put("user_login", session.getUser().getLogin());
                    MDC.put("session", sessionId);
                    MDC.put("real_ip", t.getRequestHeaders().getFirst("x-forwarded-for"));
                }catch (Exception ex){
                    log.warn("error working with MDC:{}", ex);
                }

                //иначе даже при блокировки доступ у загружке будет до перегрузки страницы, прихода обновленных данных от сервера
                //todo сделать паралельно запрос с отказом по факту блокирования
                List<Long> blocks = BillingDAO.getInstance().checkBlockedState(session.getUser().getLogin());
                if (blocks.size() != 0) {
                    while (t.getRequestBody().read() != -1) {
                    }
                    StringJoiner joiner = new StringJoiner(",");
                    blocks.forEach(e -> joiner.add(String.valueOf(e)));
                    String response = joiner.toString();
                    t.sendResponseHeaders(402, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
            if (t.getRequestHeaders().containsKey("v")) {
                frontVersion = t.getRequestHeaders().get("v").get(0);
            }
        }
        String fileName = null;
        if (t.getRequestHeaders().containsKey("filename")) {
            fileName = java.net.URLDecoder.decode(t.getRequestHeaders().get("filename").get(0), "UTF-8");
        }

        String avatarId = null;
        if (t.getRequestHeaders().containsKey("avatar")) {
            avatarId = t.getRequestHeaders().get("avatar").get(0);
        }

        GOST3411 gostDigest = null;
        if (avatarId == null) {
            // Skip init hash for avatar loading
            gostDigest = CryptoManager.getGost34311();
            gostDigest.init();
        }
        if (t.getRequestHeaders().containsKey("Content-Type") && (t.getRequestHeaders().get("Content-Type").toString().contains("multipart/form-data") || t.getRequestHeaders().get("Content-Type").toString().contains("application/octet-stream"))) {
            multipart = true;
        }
        File uploadedFileLocation = File.createTempFile("temp", Long.toString(System.nanoTime()));

        OutputStream fos = null;
        int firstLineSize = 0;
        try {
            int l;
            byte[] preBuffer = new byte[16 * 1024];
            byte[] buffer;

            InputStream is = t.getRequestBody();
            l = is.read(preBuffer);
            if (l > -1) {
                fos = new FileOutputStream(uploadedFileLocation);
                int startFrom = 0;
                boolean win = true;

                while (l != -1) {
                    int len = l;
                    buffer = preBuffer.clone();
                    if (multipart) {
                        if (firstLineSize == 0) {
                            for (int i = 0; i < l - 2; i++) {
                                if (firstLineSize == 0 && buffer[i] == (byte) '\n') {
                                    firstLineSize = i;
                                    continue;
                                }
                                if (buffer[i] == '\n' && buffer[i + 1] == (byte) '\n') {
                                    startFrom = i + 2;
                                    win = false;
                                    break;
                                }

                                if (buffer[i] == '\n' && buffer[i + 1] == (byte) '\r' && buffer[i + 2] == (byte) '\n') {
                                    startFrom = i + 3;
                                    break;
                                }
                            }
                        } else {
                            startFrom = 0;
                        }
                    }
                    l = is.read(preBuffer);

                    if (l == -1 && firstLineSize > 0) {
                        len -= (firstLineSize);
                        if (win) {
                            len -= 5;
                        } else {
                            len -= 3;
                        }

                    }
                    if (startFrom > 0) {
                        String s = new String(Arrays.copyOfRange(buffer, 0, startFrom));
                        int start = s.indexOf("filename=");

                        if (start != -1) {
                            start += 10;
                            int stop = s.indexOf("\"", start);
                            if (stop != -1 && fileName == null) {
                                fileName = s.substring(start, stop);
                            }
                        }
                    }
                    fos.write(buffer, startFrom, len - startFrom);
                    if (avatarId == null) {
                        gostDigest.update(buffer, startFrom, len - startFrom);
                    }
                }
                fos.flush();
            } else {
                String response = "Incorrect data";
                t.sendResponseHeaders(400, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                Analytics.getInstance().saveEvent(Analytics.Event.upload, new Date(), session.getUser().getLogin(), frontVersion, "unsuccessful");
            }
        } catch (FileNotFoundException e) {
            Main.log.error(e.toString());
            String response = "Can't save file";
            t.sendResponseHeaders(500, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
            Analytics.getInstance().saveEvent(Analytics.Event.upload, new Date(), session.getUser().getLogin(), frontVersion, "unsuccessful");
        } finally {
            if (fos != null) fos.close();
        }
        String newName = avatarId == null ? Base64.getUrlEncoder().withoutPadding().encodeToString(gostDigest.doFinal()) : null;

        if (avatarId != null) {
            // Сохраняется аватар пользователя
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                if (!Files.isDirectory(Paths.get(Main.CDNPath, "avatars"))) {
                    Files.createDirectories(Paths.get(Main.CDNPath, "avatars"));
                }
                BufferedImage bi = ImageIO.read(uploadedFileLocation);
                ResampleOp res = new ResampleOp(128, 128);
                res.filter(bi, null);
                ImageIO.write(bi, "png", bos);
                avatarId = Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtils.getSha256Digest().digest(session.getUser().getLogin().getBytes()));
                FileOutputStream fios = new FileOutputStream(Paths.get(Main.CDNPath, "avatars", avatarId + ".png").toFile());
                bos.writeTo(fios);
                fios.close();
                String response = Base64.getEncoder().encodeToString(bos.toByteArray());
                t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                bos.close();
            } catch (IOException e) {
                Main.log.error("Can's save avatar for login " + session.getUser().getLogin(), e);
                String response = "Unsupported Media Type";
                t.sendResponseHeaders(415, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        } else {
            ArrayList<String> cmsSignsWithoutData = new ArrayList<>();

            /* Проверка наличия cms подписи. */
            byte[] pdfOrig = Files.readAllBytes(uploadedFileLocation.toPath());
            byte[] pdfWithCmsSign = getLastCmsFromMedoc(pdfOrig);
            try {
                cmsSignsWithoutData = CryptoManager.getCmsSignsWithoutData(pdfWithCmsSign != null ? pdfWithCmsSign : pdfOrig);
            } catch (Exception ignore) {
            }

            // Сохраняется документ пользователя
            int type = ResourceDTO.detectType(detector.detect(uploadedFileLocation));
            if (type != ResourceDTO.TYPE_PDF) {
                Files.delete(uploadedFileLocation.toPath());
                String response = "Unsupported Media Type";
                t.sendResponseHeaders(415, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                Analytics.getInstance().saveEvent(Analytics.Event.upload, new Date(), session.getUser().getLogin(), frontVersion, "unsuccessful");
                return;
            }

            if (fileName.endsWith(".pdf")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            if (fileName.length() > 100) {
                fileName = fileName.substring(0, 100);
            }
            fileName = fileName.replace("<", "&#60;").replace(">", "&#62;");

            Path cdnPDFPath = Paths.get(Main.CDNPath, format.format(new Date()), newName);
            Path cdnPNGPath = Paths.get(Main.CDNPath, format.format(new Date()), newName + Main.property.getProperty("pngPrefix"));
            String path = Paths.get(Main.CDNPath).relativize(cdnPDFPath).getParent().toString().replace("\\", "/");

            // Если ресурс в системе под другим пользователем - клонировать ссылку на ресурс
            ResourceDTO oldRes = ResourceDAO.getInstance().copy(newName, fileName, session.getUser(), cmsSignsWithoutData, path, StringUtils.isEmpty(frontVersion));
            if (oldRes != null) {
                log.info("upload:resource found by hash:{}", oldRes);
                Analytics.getInstance().saveEvent(Analytics.Event.upload, new Date(), session.getUser().getLogin(), frontVersion, "successful");
                Files.delete(uploadedFileLocation.toPath());
                byte[] response = ResourceDTO.gson.toJson(oldRes).getBytes("UTF-8");
                t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                t.sendResponseHeaders(200, response.length);
                OutputStream os = t.getResponseBody();
                os.write(response);
                os.close();
                BillingHelper.getInstance().create(oldRes);
                return;
            }

            ResourceDTO newResource = null;
            try {
                File files = Paths.get(Main.CDNPath, format.format(new Date())).toFile();
                if (!files.exists()) {
                    if (!files.mkdirs()) {
                        Main.log.error("Can't move file");
                        String response = "Can't move file";
                        t.sendResponseHeaders(500, response.length());
                        OutputStream os = t.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                        return;
                    }
                }

                Files.move(uploadedFileLocation.toPath(), cdnPDFPath, StandardCopyOption.REPLACE_EXISTING);
                Main.renderQueue.add(cdnPDFPath.toFile());
                long size = Files.size(cdnPDFPath);
                newResource = ResourceDAO.getInstance().create(newName, path, fileName, session.getUser(), type, size, cmsSignsWithoutData, StringUtils.isEmpty(frontVersion));
                BillingHelper.getInstance().create(newResource);
                byte[] response = ResourceDTO.gson.toJson(newResource).getBytes("UTF-8");
                t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                t.sendResponseHeaders(200, response.length);
                OutputStream os = t.getResponseBody();
                try {
                    os.write(response);
                    os.close();
                }catch (Exception ex){
                    log.warn("likely broken pipe error: {}", ex.getMessage());
                }
                try {
                    log.info("upload file:{}:{}:{}:{}:res->{}", session.getSessionId(), session.getUser().getLogin(), fileName, size, newResource, Event.ADD_RESOURCE);
                    Analytics.getInstance().saveEvent(Analytics.Event.upload, new Date(), session.getUser().getLogin(), frontVersion, "successful");
                } catch (Exception ex) {
                    log.error("error log upload file", ex);
                }
            } catch (IOException e) {
                log.error("error upload file", e);
                if (cdnPDFPath.toFile().exists()) {
                    Files.delete(cdnPDFPath);
                }
                if (cdnPNGPath.toFile().exists()) {
                    Files.delete(cdnPNGPath);
                }
                if (newResource != null) {
                    ResourceDAO.getInstance().delete(session.getUser().getLogin(), newResource.getId());
                }
                String response = "Can't create resource";
                t.sendResponseHeaders(500, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } finally {
                if (uploadedFileLocation.exists()) {
                    Files.delete(uploadedFileLocation.toPath());
                }
            }
        }
    }

    private static Map<String, byte[]> parseTLV(byte[] tlv, int offset) throws IOException {
        HashMap<String, byte[]> ans = new HashMap<>();

        while (offset < tlv.length) {
            int lenOff;
            for (lenOff = offset; lenOff < tlv.length && tlv[lenOff] != 0; lenOff++) ;
            lenOff++;

            if (lenOff + 4 > tlv.length) {
                throw new IOException("Invalid tlv");
            }

            String tag = new String(tlv, offset, lenOff - offset - 1);

            int valueLen = ((tlv[lenOff + 3] & 0xff) << 24) | ((tlv[lenOff + 2] & 0xff) << 16) | ((tlv[lenOff + 1] & 0xff) << 8) | (tlv[lenOff] & 0xff);
            if (lenOff + 4 + valueLen > tlv.length) {
                throw new IOException("Invalid tlv");
            }

            byte[] value = new byte[valueLen];
            System.arraycopy(tlv, lenOff + 4, value, 0, valueLen);

            ans.put(tag, value);

            offset = lenOff + 4 + valueLen;
            if (offset > tlv.length) {
                throw new IOException("Invalid tlv");
            }
        }
        return ans;
    }

    private static byte[] getLastCmsFromMedoc(byte[] origPdf) {
        return CryptoManager.getLastCmsFromMedoc(origPdf);
    }
}
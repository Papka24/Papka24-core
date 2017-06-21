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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.tika.io.IOUtils;
import ua.papka24.server.Main;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CaptchaHelper {
    private static HttpClient httpclient = HttpClients.createDefault();

    public static boolean checkCaptcha(String request, String ip) {
        try {
            HttpPost httppost = new HttpPost("https://www.google.com/recaptcha/api/siteverify");

            // Request parameters and other properties.
            List<NameValuePair> params = new ArrayList<>(2);
            params.add(new BasicNameValuePair("secret", Main.recaptchaSecret));
            params.add(new BasicNameValuePair("response", request));
            if (ip != null) {
                params.add(new BasicNameValuePair("remoteip", ip));
            }
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));


            //Execute and get the response.
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                try (InputStream is = entity.getContent()) {
                    String answer = IOUtils.toString(is, "UTF-8");
                    return answer.replaceAll("\\s+", "").contains("\"success\":true");
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return false;

    }
}

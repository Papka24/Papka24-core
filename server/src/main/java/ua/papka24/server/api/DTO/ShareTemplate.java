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

package ua.papka24.server.api.DTO;

import java.util.Map;


public class ShareTemplate {
    private String name;
    private Map<String,String> params;
    private String lang;
    public static final transient String DEFAULT_LANG = "ua";

    public ShareTemplate(String lang, String name, Map<String, String> params) {
        this.lang = lang;
        this.name = name;
        this.params = params;
    }

    public ShareTemplate(String name, Map<String, String> params) {
       this("ua", name, params);
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getLang() {
        return lang;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ShareTemplate{");
        sb.append("name='").append(name).append('\'');
        sb.append(", params=").append(params);
        sb.append(", lang='").append(lang).append('\'');
        sb.append('}');
        return sb.toString();
    }
}

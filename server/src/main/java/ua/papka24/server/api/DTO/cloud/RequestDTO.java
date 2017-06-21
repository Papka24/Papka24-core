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

package ua.papka24.server.api.DTO.cloud;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;


public class RequestDTO {
    public static TypeAdapter<RequestDTO> gson = new Gson().getAdapter(RequestDTO.class);

    // URL на страницу Объекта, в котором была нажата кнопка «Подписать»
    private String urlForUpdatePage;

    // URL для возврата подписанных данных, не обязательный параметр
    private String urlForSignedData;

    // Набор подписываемых хешей
    private ArrayList<Items> items;

    public RequestDTO(HashSet<byte[]> data, String docIdentifier, String urlForSignedData, String urlForUpdatePage) {
        this.urlForSignedData = urlForSignedData;
        this.urlForUpdatePage = urlForUpdatePage;
        items = new ArrayList<>(data.size());
        Base64.Encoder encoder = Base64.getUrlEncoder();
        for (byte[] h : data) {
            items.add(new Items(encoder.encodeToString(h),docIdentifier));
        }
    }

    class Items{
        // хеш данных которые необходимо подписать
        String hash;
        // идентификатор подписываемого Объекта в системе Пользователя
        String docIdentifier;

        public Items(String hash, String docIdentifier){
            this.hash = hash;
            this.docIdentifier = docIdentifier;
        }
    }
}

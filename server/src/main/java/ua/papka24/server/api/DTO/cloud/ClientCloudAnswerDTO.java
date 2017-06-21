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


public class ClientCloudAnswerDTO {
    public static TypeAdapter<ClientCloudAnswerDTO> gson = new Gson().getAdapter(ClientCloudAnswerDTO.class);

    private long resp_cod;
    private Message message;
    private String hash;

    public long getRespCode() {
        return resp_cod;
    }

    public String getUrl() {
        if (message != null) {
            return message.url;
        } else {
            return null;
        }
    }

    public ArrayList<SignItem> getSigns() {
        ArrayList<SignItem> result = new ArrayList<>(message.items.size());
        for (Items item : message.items) {
            result.add(new SignItem(item.getHash(), item.getSigns()));
        }

        return result;
    }
}

class Message {
    String url;
    ArrayList<Items> items;
}

class Items{
    // хеш данных которые необходимо подписать
    private String hash;
    // идентификатор подписываемого Объекта в системе Пользователя
    private String docIdentifier;
    private ArrayList<String> sign;

    public ArrayList<String> getSigns() {
        return sign;
    }

    public String getHash() {
        return hash;
    }
}


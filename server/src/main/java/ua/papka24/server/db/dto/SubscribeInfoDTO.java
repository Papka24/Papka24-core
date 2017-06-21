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

package ua.papka24.server.db.dto;

import java.util.Arrays;


public class SubscribeInfoDTO {
    private String id;
    private String url;
    private int type = SubscribeType.OUT.getType();
    private String author;
    private String[] eventTypes;

    public SubscribeInfoDTO(String id, String url) {
        this.setId(id);
        this.setUrl(url);
    }

    /**
     * длу удаления
     * @param id
     */
    public SubscribeInfoDTO(String id) {
        this.setId(id);
    }

    public SubscribeInfoDTO(String id, String url, String author, int type, String[] eventsType) {
        this.setId(id);
        this.setUrl(url);
        this.author = author;
        this.type = type;
        this.eventTypes = eventsType;
    }

    public String getId() {
        return id.toLowerCase();
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getType() {
        if(type==0){
            type = SubscribeType.OUT.getType();
        }
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String[] getEventTypes() {
        if(eventTypes==null || eventTypes.length==0){
            return new String[]{};
        }
        return Arrays.stream(eventTypes).map(String::toUpperCase).toArray(String[]::new);
    }

    public void setEventTypes(String[] eventTypes) {
        this.eventTypes = eventTypes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubscribeInfo{");
        sb.append("id='").append(id).append('\'');
        sb.append(", url='").append(url).append('\'');
        sb.append(", type=").append(type);
        sb.append(", author='").append(author).append('\'');
        sb.append(", eventTypes=").append(Arrays.toString(eventTypes));
        sb.append('}');
        return sb.toString();
    }

    public enum SubscribeType{
        OUT     (1),
        IN      (2),
        GROUP   (3);

        SubscribeType(int i){
            this.type = i;
        }

        private int type;

        public int getType(){
            return this.type;
        }
    }
}

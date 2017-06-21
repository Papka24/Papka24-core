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

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Class for code verify info.
 */
public class NameDTO {
    public static TypeAdapter<NameDTO> gson = new Gson().getAdapter(NameDTO.class);
    public String C;        //COUNTRY_NAME
    public String SN;       //SERIAL_NUMBER
    public String KI;       //KNOWLEDGE_INFORMATION
    public String CN;       //COMMON_NAME
    public String SRN;      //SURNAME
    public String L;        //LOCALITY_NAME
    public String ST;       //STATE_NAME
    public String STR;      //STREET_NAME
    public String O;        //ORGANIZATION_NAME
    public String OU;       //ORGANIZATION_UNIT
    public String T;        //TITLE
    public String DE;       //DESCRIPTION
    public String BC;       //BUSINESS_CATEGORY
    public String PC;       //POSTAL_CODE
    public String PB;       //POST_OFFICE_BOX
    public String PDON;     //DELIVERY_NAME
    public String GN;       //GIVEN_NAME
    public String E;        //EMAIL
    public String EDRPOU;
    public String INN;
    public String USERCODE;

    public NameDTO(HashMap<String, String> name) {
        this.C = name.get("C");
        this.SN = name.get("SERIALNUMBER");
        this.KI = name.get("KI");
        this.CN = name.get("CN");
        this.SRN = name.get("SURNAME");
        this.L = name.get("L");
        this.ST = name.get("ST");
        this.STR = name.get("STR");
        this.O = name.get("O");
        this.OU = name.get("OU");
        this.T = name.get("T");
        this.DE = name.get("DE");
        this.BC = name.get("BC");
        this.PC = name.get("PC");
        this.PB = name.get("PB");
        this.PDON = name.get("PDON");
        this.GN = name.get("GIVENNAME");
        this.E = name.get("E");
        this.EDRPOU = null;
        this.INN = null;
        this.USERCODE = null;
    }

    public void setEDRPOU(String EDRPOU) {
        this.EDRPOU = EDRPOU;
    }

    public void setINN(String INN) {
        this.INN = INN;
    }

    public void setUSERCODE(String USERCODE) {
        this.USERCODE = USERCODE;
    }
}

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

package ua.papka24.server.utils.datetime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class StringToDateParser {

    private static final String maxFormat   = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final String fullFormat  = "yyyyMMdd HH:mm:ss";
    private static final String dirtFormat  = "yyyyMMddHHmmss";
    private static final String format      = "yyyyMMdd";
    private static final String yearMonth   = "yyyyMM";
    private static final String shortFormat = "MMdd";


    public static String format(Date date, int length ){
        SimpleDateFormat sdf;
        switch (length){
            default:
            case 8:{
                sdf = new SimpleDateFormat(format);
                return sdf.format(date);
            }
        }
    }


    /**
     * перевод даты записанной в строке в дату
     * @param dateString дата записанная в строке, поддерживаемый формат yyyyMMdd HH:mm:ss, yyyyMMdd, MMdd (за год береться текущий год)
     * @return обьект даты
     * @throws ParseException при не возможности распарсить в дату входные данные
     */
    public static Date getDate(String dateString) throws ParseException {
        SimpleDateFormat sdf;
        switch (dateString.length()){
            case 28:{
                sdf = new SimpleDateFormat(maxFormat);
                return sdf.parse(dateString);
            }
            default:
            case 17:{
                sdf = new SimpleDateFormat(fullFormat);
                return sdf.parse(dateString);
            }
            case 14:{
                sdf = new SimpleDateFormat(dirtFormat);
                return sdf.parse(dateString);
            }
            case 8: {
                sdf = new SimpleDateFormat(format);
                return sdf.parse(dateString);
            }
            case 6:{
                sdf = new SimpleDateFormat(yearMonth);
                return sdf.parse(dateString);
            }
            case 4:{
                sdf = new SimpleDateFormat(shortFormat);
                Date date = sdf.parse(dateString);
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                cal.setTime(date);
                cal.set(Calendar.YEAR,year);
                return cal.getTime();
            }
        }
    }
}

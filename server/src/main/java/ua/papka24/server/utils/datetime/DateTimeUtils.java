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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;


public class DateTimeUtils {
    public static final long MONTH = 60L * 60 * 24 * 31 * 1000;
    public static final long ONE_WEEK = 7L * 24 * 60 * 60 * 1000;
    public static final long SECONDS_30 = 30L * 1000;
    public static final long ONE_HOUR = 60L * 60 * 1000;
    public static final long MINUTE = 60L * 1000;
    public static final long A_60_DAY_DIFF = 60L * 24 * 60 * 60 * 1000;

    public static long getStartOfMonth(){
        LocalDateTime firstDayOfMonth = LocalDateTime.now().withDayOfMonth(1);
        LocalDateTime monthStartTime = firstDayOfMonth.toLocalDate().atStartOfDay();
        return Date.from(monthStartTime.atZone(ZoneId.systemDefault()).toInstant()).getTime();
    }

    public static long addNDays(Long from, int number){
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        cal.add(Calendar.DAY_OF_YEAR, number);
        return cal.getTimeInMillis();
    }
}

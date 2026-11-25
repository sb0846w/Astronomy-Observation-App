package com.astrolog.app.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Converts between the storage format (yyyy-MM-dd, sorts correctly in SQL)
 * and a friendly display format (e.g. "Fri 05 Dec 2026").
 */
public final class DateUtils {

    public static final String STORAGE_DATE = "yyyy-MM-dd";
    private static final String DISPLAY_DATE = "EEE dd MMM yyyy";

    private DateUtils() { }

    public static String toStorage(Calendar cal) {
        return new SimpleDateFormat(STORAGE_DATE, Locale.UK).format(cal.getTime());
    }

    public static String toDisplay(String storageDate) {
        if (storageDate == null || storageDate.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat(STORAGE_DATE, Locale.UK).parse(storageDate);
            return d == null ? storageDate : new SimpleDateFormat(DISPLAY_DATE, Locale.UK).format(d);
        } catch (ParseException e) {
            return storageDate;
        }
    }

    /** Parses a storage date into a Calendar (today if it can't be parsed). */
    public static Calendar parse(String storageDate) {
        Calendar cal = Calendar.getInstance();
        if (storageDate != null) {
            try {
                Date d = new SimpleDateFormat(STORAGE_DATE, Locale.UK).parse(storageDate);
                if (d != null) cal.setTime(d);
            } catch (ParseException ignored) {
                // keep today's date
            }
        }
        return cal;
    }

    public static String formatTime(int hour, int minute) {
        return String.format(Locale.UK, "%02d:%02d", hour, minute);
    }
}

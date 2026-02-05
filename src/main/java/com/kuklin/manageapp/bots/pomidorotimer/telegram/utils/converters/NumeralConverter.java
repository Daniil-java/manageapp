package com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters;

public class NumeralConverter {
    /*
        Safely parsing positive numerical string to long
        Return: -1 - if string isn't numerical or negative
     */
    public static long parsePositiveSafelyLong(String str) {
        if (str == null) return -1;
        long num;
        try {
            num = Long.parseLong(str);
            if (num < 0) return -1;
            return num;
        } catch (NumberFormatException e) {
            return -1l;
        }
    }

    /*
        Safely parsing positive numerical string to int
        Return: -1 - if string isn't numerical or negative
     */
    public static int parsePositiveSafelyInt(String str) {
        if (str == null) return -1;
        int integer;
        try {
            integer = Integer.parseInt(str);
            if (integer < 0) return -1;
            return integer;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

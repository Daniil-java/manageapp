package com.kuklin.manageapp.bots.deparrbot.models;

import com.kuklin.manageapp.bots.deparrbot.providers.AirlineCodes;

/**
 * Номер рейса в нормализованном виде: IATA-код перевозчика + номер без ведущих нулей.
 * Понимает ввод "AK 880", "ak880", "AK0880", а также ICAO-код ("AXM880" → AK/880).
 */
public record FlightCode(String carrier, String number) {

    /**
     * @param raw текст после команды, например "AK 880" или "AK880"
     * @return нормализованный код или null, если строку не удалось разобрать
     */
    public static FlightCode parseOrNull(String raw) {
        if (raw == null) return null;
        String text = raw.replace(" ", "").replace("-", "").toUpperCase();
        if (text.length() < 3) return null;

        // ICAO: три буквы и дальше цифры (AXM880). IATA: два символа (AK880, 3K533, VJ801)
        int carrierLength = Character.isLetter(text.charAt(0))
                && Character.isLetter(text.charAt(1))
                && Character.isLetter(text.charAt(2)) ? 3 : 2;

        String carrier = text.substring(0, carrierLength);
        String number = stripLeadingZeros(text.substring(carrierLength));
        if (number.isEmpty() || !isFlightNumber(number)) return null;

        if (carrierLength == 3) {
            String iata = AirlineCodes.findIataOrNull(carrier);
            if (iata == null) return null;
            carrier = iata;
        }
        return new FlightCode(carrier, number);
    }

    public String getIcaoIdentOrNull() {
        String icao = AirlineCodes.findIcaoOrNull(carrier);
        return icao == null ? null : icao + number;
    }

    public String getDisplayName() {
        return carrier + number;
    }

    private static String stripLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    /** Цифры, за которыми может идти одна буква-суффикс (например 123A). */
    private static boolean isFlightNumber(String s) {
        int digits = 0;
        while (digits < s.length() && Character.isDigit(s.charAt(digits))) digits++;
        if (digits == 0 || digits > 4) return false;
        int rest = s.length() - digits;
        return rest == 0 || (rest == 1 && Character.isLetter(s.charAt(digits)));
    }
}

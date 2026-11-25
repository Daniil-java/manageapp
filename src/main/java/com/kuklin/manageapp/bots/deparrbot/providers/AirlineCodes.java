package com.kuklin.manageapp.bots.deparrbot.providers;

import java.util.HashMap;
import java.util.Map;

/**
 * IATA ↔ ICAO коды авиакомпаний. FlightAware ищет рейсы только по ICAO (AXM880, а не AK880).
 * В основном Юго-Восточная Азия плюс крупные перевозчики, которые туда летают.
 */
public final class AirlineCodes {
    private static final Map<String, String> IATA_TO_ICAO = new HashMap<>();
    private static final Map<String, String> ICAO_TO_IATA = new HashMap<>();

    static {
        // Малайзия
        add("AK", "AXM"); // AirAsia
        add("D7", "XAX"); // AirAsia X
        add("MH", "MAS"); // Malaysia Airlines
        add("OD", "MXD"); // Batik Air Malaysia
        add("FY", "FFM"); // Firefly
        // Таиланд
        add("FD", "AIQ"); // Thai AirAsia
        add("XJ", "TAX"); // Thai AirAsia X
        add("TG", "THA"); // Thai Airways
        add("PG", "BKP"); // Bangkok Airways
        add("DD", "NOK"); // Nok Air
        add("SL", "TLM"); // Thai Lion Air
        add("VZ", "TVJ"); // Thai VietJet
        // Вьетнам
        add("VJ", "VJC"); // VietJet
        add("VN", "HVN"); // Vietnam Airlines
        add("QH", "BAV"); // Bamboo Airways
        add("VU", "VAG"); // Vietravel Airlines
        // Индонезия
        add("QZ", "AWQ"); // Indonesia AirAsia
        add("JT", "LNI"); // Lion Air
        add("ID", "BTK"); // Batik Air
        add("IW", "WON"); // Wings Air
        add("IU", "SJV"); // Super Air Jet
        add("GA", "GIA"); // Garuda Indonesia
        add("QG", "CTV"); // Citilink
        add("SJ", "SJY"); // Sriwijaya Air
        // Лаос, Камбоджа, Мьянма
        add("QV", "LAO"); // Lao Airlines
        add("8M", "MMA"); // Myanmar Airways International
        // Филиппины
        add("Z2", "APG"); // Philippines AirAsia
        add("PR", "PAL"); // Philippine Airlines
        add("5J", "CEB"); // Cebu Pacific
        // Сингапур, Гонконг
        add("SQ", "SIA"); // Singapore Airlines
        add("TR", "TGW"); // Scoot
        add("3K", "JSA"); // Jetstar Asia
        add("CX", "CPA"); // Cathay Pacific
        add("UO", "HKE"); // HK Express
        // Транзитные и дальнемагистральные
        add("SU", "AFL"); // Аэрофлот
        add("S7", "SBI"); // S7
        add("EK", "UAE"); // Emirates
        add("QR", "QTR"); // Qatar Airways
        add("EY", "ETD"); // Etihad
        add("TK", "THY"); // Turkish Airlines
        add("KE", "KAL"); // Korean Air
        add("OZ", "AAR"); // Asiana
        add("JL", "JAL"); // Japan Airlines
        add("NH", "ANA"); // ANA
        add("CA", "CCA"); // Air China
        add("CZ", "CSN"); // China Southern
        add("MU", "CES"); // China Eastern
        add("BR", "EVA"); // EVA Air
        add("CI", "CAL"); // China Airlines
        add("6E", "IGO"); // IndiGo
        add("AI", "AIC"); // Air India
    }

    private AirlineCodes() {
    }

    private static void add(String iata, String icao) {
        IATA_TO_ICAO.put(iata, icao);
        ICAO_TO_IATA.put(icao, iata);
    }

    public static String findIcaoOrNull(String iata) {
        return IATA_TO_ICAO.get(iata);
    }

    public static String findIataOrNull(String icao) {
        return ICAO_TO_IATA.get(icao);
    }
}

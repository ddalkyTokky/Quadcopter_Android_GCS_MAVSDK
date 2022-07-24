package com.example.utils;

public class TextUtils {

    private TextUtils() {
    }

    public static String roundToDecimalPlaces(double x, int decimalPlaces) {
        return String.format("%." + decimalPlaces + "f", x);
    }
}

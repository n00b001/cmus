package com.yachtmafia.messages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SwapMessage {
    public static final String AMOUNT_OF_COIN_ATTRIB = "AMOUNT";
    public static final String FROM_CURRENCY_NAME_ATTRIB = "FROM_CURRENCY_NAME";
    public static final String TO_CURRENCY_NAME_ATTRIB = "TO_CURRENCY_NAME";
    public static final String USERNAME_ATTRIB = "USER_NAME";
    private final String amountOfCoin;
    private final String fromCurrencyName;
    private final String toCurrencyName;
    private final String username;

    public SwapMessage(String value) {
        JsonObject jsonObject = new JsonParser().parse(value).getAsJsonObject();
        amountOfCoin = jsonObject.get(AMOUNT_OF_COIN_ATTRIB).getAsString();
        fromCurrencyName = jsonObject.get(FROM_CURRENCY_NAME_ATTRIB).getAsString();
        toCurrencyName = jsonObject.get(TO_CURRENCY_NAME_ATTRIB).getAsString();
        username = jsonObject.get(USERNAME_ATTRIB).getAsString();
    }

    public String getAmountOfCoin() {
        return amountOfCoin;
    }

    public String getFromCoinName() {
        return fromCurrencyName;
    }

    public String getToCoinName() {
        return toCurrencyName;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "SwapMessage{" +
                "amountOfCoin=" + amountOfCoin +
                ", fromCurrencyName='" + fromCurrencyName + '\'' +
                ", toCurrencyName='" + toCurrencyName + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}

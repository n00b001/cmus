package com.yachtmafia.bank;

import com.yachtmafia.config.Config;
import com.yachtmafia.exchange.Exchange;
import com.yachtmafia.exchange.ExchangeMock;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by xfant on 2018-01-08.
 */
public class BankImplTest {
    BankImpl bank;

    @Test @Ignore
    public void payUser() throws Exception {
        bank = new BankImpl(new Config());
        String currency = "GBP";
        long amount = 100;
        String user = "PAMILA@gmail.com";
        boolean success = bank.payUser(currency, amount, user);
        assert success;
    }

    @Test
    public void toExchange() throws Exception {
        bank = new BankImpl(new Config());
        Exchange exchange = new ExchangeMock();
        String currency = "GBP";
        long amount = 100;
        boolean success = bank.transferFromBankToExchange(currency, amount, exchange);
        assert success;
    }
}
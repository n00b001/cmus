package com.yachtmafia.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bitcoinj.store.BlockStoreException;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.yachtmafia.util.KafkaMessageGenerator.*;

public class WithdrawHandlerTest {
    private MessageHandler messageHandler;

    @Before
    public void setup() throws BlockStoreException {
        HandlerDAO handlerDAO = new HandlerDAOMock();
        ExecutorService handlerPool = Executors.newFixedThreadPool(3);
        messageHandler = new WithdrawHandler(handlerDAO, handlerPool);
    }

    @Test
    public void processMessage() throws ExecutionException, InterruptedException {
        List<ConsumerRecord<String, String>> records = getWithdrawMessages(100);

        for (ConsumerRecord<String, String> cr : records) {
            assert messageHandler.run(cr).get();
        }

        records = getDepositMessages(100);

        for (ConsumerRecord<String, String> cr : records) {
            assert !messageHandler.run(cr).get();
        }

        records = getSwapMessages(100);

        for (ConsumerRecord<String, String> cr : records) {
            assert !messageHandler.run(cr).get();
        }
    }
}
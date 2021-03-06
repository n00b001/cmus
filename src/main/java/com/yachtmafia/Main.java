package com.yachtmafia;

import com.yachtmafia.bank.Bank;
import com.yachtmafia.bank.BankImpl;
import com.yachtmafia.config.Config;
import com.yachtmafia.db.DBWrapper;
import com.yachtmafia.db.DBWrapperImpl;
import com.yachtmafia.exchange.ExchangeCoinbase;
import com.yachtmafia.exchange.ExchangeGdax;
import com.yachtmafia.exchange.ExchangeWrapper;
import com.yachtmafia.handlers.*;
import com.yachtmafia.kafka.Consumer;
import com.yachtmafia.walletwrapper.WalletWrapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import java.util.Scanner;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.yachtmafia.util.Const.*;

public class Main implements Thread.UncaughtExceptionHandler{
    private static final Logger logger = LogManager.getLogger(Main.class);

//    private final Logger LOG = Logger.getLogger(getClass().getSimpleName());

//    private final Logger LOG = LoggerFactory.getLogger(getClass());
    //    private Consumer consumer = new Consumer();
//    private Producer producer = new Producer();
    private List<Thread> threads = new ArrayList<>();
    private volatile boolean running = true;
    private DBWrapper dbWrapper;
    private Bank bank;
    private ExchangeWrapper exchange;
    private HandlerDAO handlerDAO;
    private Config config;
    private WalletWrapper walletWrapper;
    private NetworkParameters network;
    private Thread inputThread;
//    private Thread peerGroupThread;
    private AbstractBlockChain chain;
    private PeerGroup peerGroup;

    public static void main(String[] args) throws BlockStoreException {
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        File file = new File("src/main/resources/log4j.xml");

// this will force a reconfiguration
        context.setConfigLocation(file.toURI());

        Main main = new Main();
        main.run();
    }

    private void run() throws BlockStoreException {
        setupAll();
//        startProducer();
        startAllThreads();
        waitForThreadsToFinish();
    }

    private void setupAll() throws BlockStoreException {
        setupUnhandledExceptions();
        setupUserInput();
        setupConfig();
        setupNetwork();
        setupDBWrapper();
        setupBank();
        setupExchange();
        setupChain();
        setupPeerGroup();
        setupWalletWrapper();
        setupHandlerDao();
        setupConsumers();
    }

    private void setupPeerGroup() {
        peerGroup = new PeerGroup(network, chain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(network));
//        peerGroupThread = new Thread(() -> peerGroup.downloadBlockChain());
    }

    private void setupHandlerDao() {
        handlerDAO = new HandlerDAO(dbWrapper, bank, exchange, walletWrapper,
                config, network, peerGroup, chain);
    }

    private void setupUserInput() {
        inputThread = new Thread(() -> {

            Scanner scan = new Scanner(System.in);
            String input;
            while (running) {
                logger.info("Type Q to exit: ");
                input = scan.nextLine();
                switch (input){
                    case "q":
                    case "Q":
                        logger.info("Shutting down from user input!");
                        running = false;
                        break;
                    default:
                        logger.info("Unknown input: " + input);
                }
            }
            logger.info("Shutting down input handler...");
        });
    }

    private void setupNetwork() {
        network = NetworkParameters.fromID(config.NETWORK);
    }

    private void setupWalletWrapper() {
        File file = new File("wallet");
        WalletAppKit walletAppKit = new WalletAppKit(network,
                file, "forwarding-service-main" + network.toString());

        Web3j web3j = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
        walletWrapper = new WalletWrapper(walletAppKit, web3j);
    }

    private void setupConfig() {
        this.config = new Config();
    }

    private void setupExchange() {
        exchange = new ExchangeWrapper(config);
        exchange.addExchange(new ExchangeCoinbase(config));
        exchange.addExchange(new ExchangeGdax(config));
    }

    private void setupBank() {
        bank = new BankImpl(config);
    }

    private void setupDBWrapper() {
        dbWrapper = new DBWrapperImpl(config);
    }
    private void setupChain() throws BlockStoreException {
        BlockStore blockstore = new SPVBlockStore(network,
                new File("blockstore" + network.getId()));
        chain = new BlockChain(network, blockstore);
    }

    private void setupUnhandledExceptions() {
        for (Thread t : threads){
            t.setUncaughtExceptionHandler(this);
        }
    }

    private void startAllThreads() {
        logger.info("Starting all threads...");
        inputThread.start();
//        peerGroupThread.start();
        List<ECKey> list = new ArrayList<>();
        ECKey key = new ECKey();
        key.setCreationTimeSeconds(0);
        list.add(key);
        Wallet wallet = Wallet.fromKeys(network, list);
        peerGroup.addWallet(wallet);
        chain.addWallet(wallet);
        peerGroup.start();
        peerGroup.downloadBlockChain();
//        walletWrapper.getBitcoinWalletAppKit().setAutoSave(true);
//        walletWrapper.startAsync();
//        walletWrapper.getBitcoinWalletAppKit().awaitRunning();
//        List<ECKey> importedKeys = walletWrapper.getBitcoinWalletAppKit().wallet().getImportedKeys();
//        for (ECKey key : importedKeys) {
//            walletWrapper.getBitcoinWalletAppKit().wallet().removeKey(key);
//        }
//        walletWrapper.getBitcoinWalletAppKit().peerGroup().startAsync();
        for (Thread t : threads){
            t.start();
        }
        logger.info("All started!");
    }

//    private void startProducer() {
//        configureProducer();
//
//        threads.add(new Thread(producer));
//    }

//    private void configureProducer() {
//        //consumer properties
//        Properties props = new Properties();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
//                "35.197.252.186:9092");
////        props.put("group.id", "test-group");
//
//        //string inputs and outputs
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
//                "org.apache.kafka.common.serialization.StringSerializer");
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
//                , "org.apache.kafka.common.serialization.StringSerializer");
//
//        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "100");
//        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "100");
//
//        producer.configure(props);
//        producer.subscribe("my-topic");
//    }

    private void waitForThreadsToFinish()  {
        try {
            while (running && !Thread.interrupted()) {
                for(Thread t : threads){
                    if (t.isInterrupted() || !t.isAlive()) {
                        running = false;
                        break;
                    }
                }
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException ex){
            logger.error("caught: ", ex);
            Thread.currentThread().interrupt();
        }
//        walletWrapper.getBitcoinWalletAppKit().wallet().shutdownAutosaveAndWait();
        logger.info("Shutting down...");

        peerGroup.stop();

        inputThread.interrupt();
//        peerGroupThread.interrupt();
        try {
            inputThread.join(100);
        } catch (InterruptedException e) {
            logger.warn("Caught: ", e);
        }

        for(Thread t : threads){
            t.interrupt();
        }

        for (Thread thread : threads) {
            try {
                thread.join(100);
            } catch (InterruptedException e) {
                logger.warn("Caught: ", e);
                Thread.currentThread().interrupt();
            }
        }

        System.exit(1);
    }

    private void setupConsumers() {
        List<MessageHandler> depositListers = new ArrayList<>();
        ExecutorService handlerPool = Executors.newFixedThreadPool(config.AMOUNT_OF_HANDLER_THREADS);
        depositListers.add(new DepositHandler(handlerDAO, handlerPool));

        List<MessageHandler> withdrawListers = new ArrayList<>();
        ExecutorService withdrawPool = Executors.newFixedThreadPool(config.AMOUNT_OF_HANDLER_THREADS);
        withdrawListers.add(new WithdrawHandler(handlerDAO, withdrawPool));

        List<MessageHandler> swapListers = new ArrayList<>();
        ExecutorService swapPool = Executors.newFixedThreadPool(config.AMOUNT_OF_HANDLER_THREADS);
        swapListers.add(new SwapHandler(handlerDAO, swapPool));


        Consumer depositConsumer = configureConsumer(DEPOSIT_TOPIC_NAME, depositListers);
        Consumer withdrawConsumer = configureConsumer(WITHDRAW_TOPIC_NAME, withdrawListers);
        Consumer swapConsumer = configureConsumer(SWAP_TOPIC_NAME, swapListers);

        threads.add(new Thread(depositConsumer));
        threads.add(new Thread(withdrawConsumer));
        threads.add(new Thread(swapConsumer));
    }

    private Consumer configureConsumer(String topics, List<MessageHandler> listeners) {
        //consumer properties
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.KAFKA_ADDRESS);
        // This is the ID of this consumer machine
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "1");

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        //string inputs and outputs
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        //subscribe to topic
        Consumer consumer = new Consumer(props, listeners);
        consumer.subscribe(topics);
        return consumer;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.fatal("Caught: " + t.toString(), e);
        running = false;
    }
}

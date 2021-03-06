package com.yachtmafia.handlers;

import com.yachtmafia.cryptoKeyPairs.CryptoKeyPair;
import com.yachtmafia.cryptoKeyPairs.CryptoKeyPairGenerator;
import com.yachtmafia.messages.SwapMessage;
import com.yachtmafia.util.StatusLookup;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;

import static com.yachtmafia.util.Const.DEPOSIT_TOPIC_NAME;
import static com.yachtmafia.util.Util.sendEmail;

public class DepositHandler implements MessageHandler {
    private static final String TOPIC_NAME = DEPOSIT_TOPIC_NAME;
//    private final Logger LOG = Logger.getLogger(getClass().getSimpleName());
private static final Logger logger = LogManager.getLogger(DepositHandler.class);

    private final HandlerDAO handlerDAO;
    private ExecutorService pool;
    private ConsumerRecord<String, String> message;

    public DepositHandler(HandlerDAO handlerDAO, ExecutorService pool) {
        this.handlerDAO = handlerDAO;
        this.pool = pool;
    }

    private DepositHandler(HandlerDAO handlerDAO, ConsumerRecord<String, String> message) {
        this.handlerDAO = handlerDAO;
        this.message = message;
    }

    @Override
    public Future<Boolean> run(ConsumerRecord<String, String> message) {
        return pool.submit(new DepositHandler(handlerDAO, message));
    }

    @Override
    public Boolean call() throws Exception {
        if (TOPIC_NAME.equals(message.topic())) {
            SwapMessage swapMessage = new SwapMessage(message.value());
            addTransactionStatus(swapMessage, StatusLookup.REQUEST_RECEIVED_BY_SERVER);
            boolean success;
            logger.info("swapmessage: " + swapMessage.toString());
//            String publicAddress = handlerDAO.getDbWrapper().getPublicAddress(swapMessage.getUsername(),
//                    swapMessage.getToCoinName());
//            if (publicAddress == null){
                try {
                    CryptoKeyPair keyPair = CryptoKeyPairGenerator.parse(swapMessage.getToCoinName(),
                            handlerDAO.getNetwork());
                    success = handlerDAO.getDbWrapper().addNewWallet(swapMessage.getUsername(),
                            swapMessage.getToCoinName(),
                            keyPair.getPublicAddress(), keyPair.getPrivateKey());
                    if (!success) {
                        logger.error("Did not add wallet successfully! " + message);
                        addTransactionStatus(swapMessage, StatusLookup.COULD_NOT_ADD_WALLET);
                        return false;
                    }

                    addTransactionStatus(swapMessage, StatusLookup.WALLET_CREATED);
//            boolean success = handlerDAO.getBank().transferFromBankToExchange(swapMessage.getFromCoinName(),
//                    swapMessage.getAmountOfCoin(), handlerDAO.getExchange());
//            if (!success){
//                logError(this, "Did not transfer balance from bank to exchange! "
//                         + message);
//                return false;
//            }
//            String purchasedAmount = handlerDAO.getExchange().exchangeCurrency(swapMessage.getFromCoinName(),
//                    swapMessage.getToCoinName(), swapMessage.getAmountOfCoin());
//            if(purchasedAmount == null){
//                logError(this, "Failed to make purchase: " + message.toString());
//            }

                    String subject = "User Has made purchase: " + swapMessage.getID();
                    String bodyMessage = "Address: " + keyPair.getPublicAddress()
                            + "\n\nCoin from: " + swapMessage.getFromCoinName()
                            + "\nAmount: " + swapMessage.getAmountOfCoin()
                            + "\n\nCoin to: " + swapMessage.getToCoinName() + "\n"
                            + "\n\n\nFull message: \n" + swapMessage.toString();

                    success = sendEmail(bodyMessage, subject,
                            handlerDAO.getConfig(), handlerDAO.getConfig().EMAIL_RECIPTS);
                    if (!success){
                        logger.error("Did not email! " + message);
                        addTransactionStatus(swapMessage, StatusLookup.COULD_NOT_SEND_EMAIL);
                        return false;
                    }

                    addTransactionStatus(swapMessage, StatusLookup.SUBMITTING_TO_EXCHANGE);
                    String purchasedAmount = waitForFunds(keyPair.getPublicAddress());
                    addTransactionStatus(swapMessage, StatusLookup.VERIFYING_EXCHANGE);

//            success = handlerDAO.getExchange().withdrawCrypto(
//                    swapMessage.getToCoinName(), publicAddress, purchasedAmount);
//            if (!success){
//                logError(this, "Did not withdraw coins! " + message);
//                return false;
//            }
                    addTransactionStatus(swapMessage, StatusLookup.ADDING_TO_WALLET);
                    success = handlerDAO.getDbWrapper().addTransaction(swapMessage, purchasedAmount);
                    if (!success){
                        logger.error("Did not add portfolio balance " + message);

                        addTransactionStatus(swapMessage, StatusLookup.COULD_NOT_ADD_PORTFOLIO_BALANCE);
                        return false;
                    }

                    addTransactionStatus(swapMessage, StatusLookup.FINALISING);
                    addTransactionStatus(swapMessage, StatusLookup.SUCCESS);

                    Coin coin = Coin.valueOf(Long.valueOf(purchasedAmount));

                    subject = "Money successfully deposited";
                    bodyMessage = "Your deposit of " + swapMessage.getAmountOfCoin()
                            + swapMessage.getFromCoinName() + " is safely added into your account as "
                            + coin.toFriendlyString() + "!";

                    success = sendEmail(bodyMessage, subject,
                            handlerDAO.getConfig(), new String[]{swapMessage.getUsername()});
                    if (!success){
                        logger.error("Did not email! " + message);
                        addTransactionStatus(swapMessage, StatusLookup.COULD_NOT_SEND_EMAIL);
                        return false;
                    }
                    return true;
                }catch (Exception e){
                    logger.error("Did not add wallet successfully! " + message, e);
                    addTransactionStatus(swapMessage, StatusLookup.COULD_NOT_ADD_WALLET);
                    return false;
                }
//            }
        }
        return false;
    }

    private void addTransactionStatus(SwapMessage swapMessage, StatusLookup statusLookup) {
        boolean success = handlerDAO.getDbWrapper()
                .addTransactionStatus(swapMessage, statusLookup);
        if (!success){
            logger.error("Failed to update status " + statusLookup);
        }
    }

    private String waitForFunds(String publicAddress) throws InterruptedException {
//        privateKey = "n1UZRBRgJyFMxp6QcyyWpqvqK514K1iXzc";//todo:testing
//        NetworkParameters network = handlerDAO.getNetwork();
//        Address address = Address.fromBase58(network, privateKey);
//        ECKey ecKey = ECKey.fromPrivate(privateKey.getBytes());
        Wallet wallet = new Wallet(handlerDAO.getNetwork());
        Address publicAddr = Address.fromBase58(handlerDAO.getNetwork(), publicAddress);
        wallet.addWatchedAddress(publicAddr);
//        AddressBalance addressBalance = new AddressBalance(address);

//        Wallet wallet = handlerDAO.getWalletWrapper().getBitcoinWalletAppKit().wallet();

//        List<ECKey> list = new ArrayList<>();
//        list.add(ecKey);
//        Wallet wallet = Wallet.fromKeys(handlerDAO.getNetwork(), list);
//        Wallet.

//        Wallet wallet = Wallet.fromWatchingKeyB58(handlerDAO.getNetwork(), publicAddress,
//                DeterministicHierarchy.BIP32_STANDARDISATION_TIME_SECS);

//        Wallet wallet = new Wallet(handlerDAO.getNetwork());
//        PeerGroup peerGroup = new PeerGroup(handlerDAO.getNetwork(), handlerDAO.getChain());
//        peerGroup.addPeerDiscovery(new DnsDiscovery(handlerDAO.getNetwork()));
//        peerGroup.setMaxConnections(peerGroup.getMaxConnections());
        handlerDAO.getChain().addWallet(wallet);
        handlerDAO.getPeerGroup().addWallet(wallet);
//        handlerDAO.getPeerGroup().downloadBlockChain();
//        peerGroup.start();
//        peerGroup.downloadBlockChain();

        try {
//            boolean success = wallet.importKey(ecKey);
//            if (!success){
//                logger.error("Could not add address");
//                throw new RuntimeException("Could not add address");
//            }

//            Coin balance = wallet.getBalance();
            final Coin[] balance = new Coin[1];
            wallet.addCoinsReceivedEventListener((wallet1, tx, prevBalance, newBalance) -> {
                balance[0] = newBalance.minus(prevBalance);
            });

//        Wallet wallet = Wallet.fromWatchingKeyB58(MainNetParams.get(), publicAddress, 0);
//        Coin balance = wallet.getBalance();
            logger.info("Waiting for exchange...");
            while (balance[0] == null || balance[0].isZero()) {
                Thread.sleep(1000);
//                wallet.reset();
//                balance[0] = wallet.getBalance();
            }
            logger.info("Exchange done for value: " + balance[0].getValue() + " satoshi");
            handlerDAO.getChain().removeWallet(wallet);
            handlerDAO.getPeerGroup().removeWallet(wallet);
            return String.valueOf(balance[0].getValue());
        } catch (IllegalStateException ex) {
            logger.error("Caught: ", ex);
//            WalletAppKit walletAppKit = handlerDAO.getWalletWrapper().getBitcoinWalletAppKit();
//            if (!walletAppKit.isRunning()) {
//                throw new RuntimeException("Wallet is not running!");
//            }
            handlerDAO.getChain().removeWallet(wallet);
            handlerDAO.getPeerGroup().removeWallet(wallet);
            throw new RuntimeException(ex);
        }

//        ECKey ecKey = ECKey.fromPublicOnly()
//        List<ECKey> ecKeyList = new ArrayList<>();
//        ecKeyList.add(ecKey);
//        Wallet wallet = Wallet.fromKeys(network, ecKeyList);
//
//        org.bitcoinj.core.Address address = org.bitcoinj.core.Address.fromBase58(network, depositAddress);
//
//
//        WalletWrapper walletWrapper = handlerDAO.getWalletWrapper();
//        walletWrapper.
    }
}

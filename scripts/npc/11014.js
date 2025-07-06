var status = 0;
var NXT_COIN_ID = 3020001;

// Configurable exchange rates
var BUY_RATE = 1000000;  // NX cost to buy 1 NXT
var SELL_RATE = 500000;  // NX returned for 1 NXT sold

var modeType = -1; // 0 = buy, 1 = sell
var exchangeAmount = 0;

function start() {
    status = 0;
    cm.sendSimple(
        "Welcome to the NXT Coin Exchange Booth! I'm Mr Hong's Brother! Would you like to buy some NXT?\r\n" +
        "#L0#Buy NXT Coin (1M NX)#l\r\n" +
        "#L1#Sell NXT Coin (500K NX)#l"
    );
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    status++;

    if (status === 1) {
        modeType = selection;
        if (modeType === 0) {
            cm.sendGetText("How many NXT Coins would you like to *buy*?\r\n(1 NXT = " + BUY_RATE + " NX)");
        } else {
            cm.sendGetText("How many NXT Coins would you like to *sell*?\r\n(1 NXT = " + SELL_RATE + " NX)");
        }
    } else if (status === 2) {
        exchangeAmount = parseInt(cm.getText());

        if (isNaN(exchangeAmount) || exchangeAmount <= 0 || exchangeAmount > 1000) {
            cm.sendOk("Please enter a valid amount (1 to 1000).");
            cm.dispose();
            return;
        }

        if (modeType === 0) { // Buying NXT
            var totalNXCost = exchangeAmount * BUY_RATE;
            if (cm.getCashShop().getCash(1) < totalNXCost) {
                cm.sendOk("You do not have enough NX. You need " + totalNXCost + " NX.");
                cm.dispose();
                return;
            }

            cm.gainItem(NXT_COIN_ID, exchangeAmount);
            cm.gainCash(-totalNXCost);
            cm.sendCashNoti("You used " + totalNXCost + " NX to buy " + exchangeAmount + " NXT Coin(s).");
            cm.sendOk("Transaction successful!\r\nBought " + exchangeAmount + " NXT Coin(s) for " + totalNXCost + " NX.");
        } else { // Selling NXT
            if (!cm.haveItem(NXT_COIN_ID, exchangeAmount)) {
                cm.sendOk("You do not have enough NXT Coin(s).");
                cm.dispose();
                return;
            }

            var totalNXGain = exchangeAmount * SELL_RATE;
            cm.gainItem(NXT_COIN_ID, -exchangeAmount);
            cm.gainCash(totalNXGain);
            cm.sendCashNoti("You sold " + exchangeAmount + " NXT Coin(s) and received " + totalNXGain + " NX.");
            cm.sendOk("Transaction successful!\r\nSold " + exchangeAmount + " NXT Coin(s) for " + totalNXGain + " NX.");
        }

        cm.dispose();
    }
}

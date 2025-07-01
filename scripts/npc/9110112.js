var status = 0;
var NX_MCOIN_ID = 3020001;
var MESO_BCOIN_ID = 3020002;
var manualNumber = "";
var betType = "";
var currentDrawDate;

var FourDBetManager = Java.type("gambling.FourDBetManager");

function start() {
    status = 0;
    currentDrawDate = getDrawDate();
    cm.sendSimple("Welcome to the 4D Gambling Booth\r\n" +
        "#L0#Buy 4D Ticket#l\r\n" +
        "#L1#Check Today's Results#l\r\n" +
        "#L2#Claim Prize#l");
}

function action(mode, type, selection) {
    java.lang.System.out.println("[4D NPC] status = " + status + ", selection = " + selection);

    if (mode !== 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status === 1) {
        switch (selection) {
            case 0:
                cm.sendGetText("Enter your 4-digit number (0000–9999):");
                break;
            case 1:
                checkResults();
                return;
            case 2:
                claimPrize();
                return;
        }
    } else if (status === 2) {
        manualNumber = cm.getText();
        if (!/^\d{4}$/.test(manualNumber)) {
            cm.sendOk("Invalid number. Must be 4 digits.");
            cm.dispose();
            return;
        }
        cm.sendSimple("Choose your bet type for number " + manualNumber + ":\r\n#L0#Big Bet (more coverage, lower payout)#l\r\n#L1#Small Bet (less coverage, higher payout)#l");
    } else if (status === 3) {
        betType = (selection === 0) ? "BIG" : "SMALL";
        FourDBetManager.insertBet(cm.getPlayer().getId(), manualNumber, betType, currentDrawDate);
        cm.sendOk("You have placed a " + betType + " bet on number " + manualNumber + " for the draw on " + currentDrawDate + ".");
        cm.dispose();
    }
}

function checkResults() {
    try {
        var ResultManager = Java.type("server.gambling.FourDResultManager");
        var con = DatabaseConnection.getConnection();
        var ps = con.prepareStatement("SELECT * FROM 4d_results ORDER BY draw_date DESC LIMIT 1");
        var rs = ps.executeQuery();
        if (rs.next()) {
            var result = "4D Results for " + rs.getString("draw_date") + ":\r\n";
            result += "1st Prize: " + rs.getString("prize_1st") + "\r\n";
            result += "2nd Prize: " + rs.getString("prize_2nd") + "\r\n";
            result += "3rd Prize: " + rs.getString("prize_3rd") + "\r\n";
            result += "Starter Prizes: " + rs.getString("starters") + "\r\n";
            result += "Consolation Prizes: " + rs.getString("consolations");
            cm.sendOk(result);
        } else {
            cm.sendOk("No results available yet.");
        }
        rs.close();
        ps.close();
    } catch (e) {
        cm.sendOk("Error retrieving results.");
    }
    cm.dispose();
}

function claimPrize() {
    try {
        var unclaimed = FourDBetManager.getUnclaimedWinningBets(cm.getPlayer().getId());
        var total = 0;
        for (var i = 0; i < unclaimed.size(); i++) {
            var row = unclaimed.get(i);
            var quantity = row.get("prize_quantity");
            if (quantity > 0) {
                total += quantity;
                FourDBetManager.markBetClaimed(row.get("bet_id"));
            }
        }
        if (total > 0) {
            cm.gainItem(MESO_BCOIN_ID, total);
            cm.sendOk("You have claimed " + total + " Meso BCoins. Congratulations!");
        } else {
            cm.sendOk("You have no unclaimed prizes.");
        }
    } catch (e) {
        cm.sendOk("Error during prize claim.");
    }
    cm.dispose();
}

function getDrawDate() {
    var now = new Date();
    var cutoff = new Date();
    cutoff.setHours(18, 0, 0, 0);
    return now < cutoff ? formatDate(now) : formatDate(new Date(now.getTime() + 86400000));
}

function formatDate(date) {
    var y = date.getFullYear();
    var m = (date.getMonth() + 1).toString().padStart(2, '0');
    var d = date.getDate().toString().padStart(2, '0');
    return y + '-' + m + '-' + d;
}

// Upgrade materials
var zakDiamond   = 4032133;
var hTegg        = 4001094;
var rockOfTime   = 4021010;
var upgradeConfigRb0 = {
    1: { fee: 15000000, mats: [{ id: zakDiamond, amt: 1 }] },
    2: { fee: 45000000, mats: [{ id: zakDiamond, amt: 3 }] },
    3: { fee: 125000000, mats: [{ id: hTegg,      amt: 1 }] },
    4: { fee: 275000000, mats: [{ id: hTegg,      amt: 3 }] }
};

// Fees + Protection scroll
var previewFee   = 2500000;
var boomProtectScroll = 3020003;

//choice
var salvage         = false; // must take precedence over upgrade
var upgradeNormal   = false;
var slot            = -1;
var reroll          = false;

//Upgrade variables
var selectedItem
var newStats;

// Salvage variables
var totalUpgradeFee = 460000000;
var totalRebirthMats = {};

function start() {
    status = 0;
    cm.sendNext("Hello! I'm Slimy's Subordinate! I facilitate Weapon Upgrading and Rebirths, what do you want to do today?");
}

// ============================= Main NPC Chat Sequence =============================
function action(mode, type, selection) {
    if (mode !== 1) return cm.dispose();
    status++;

    if (status === 1) {
//        Menu:
//        0: Normal Upgrade; upgradeNormal = true
//        1: Premium Upgrade; upgradeNormal = false
//        2: Salvage > status === 39
        return Menu();
    } else if (status === 2) {
//        Weapon selection menu to select weapon
//        if upgradeNormal: status = 19 must be 1 less than intention because of function action will status += 1
//        if not upgradeNormal: status = 29
        return weaponSelection(selection)
    } else if (status === 20) {
//        preview stats with normal roll
//        2 options given here:
//          0: Reroll
//          1: Upgrade!
//          if selection == 1 : status == 20
//          else status == 19: action(1, 0, undefined)
        if (!reroll) {
            slot = selection;
        }
        return preview(slot, upgradeNormal)
    } else if (status === 21) {
//        Handle weapon upgrade -- normal tier
//        cm.dispose()
        if (selection == 0) { // reroll
            status = 19;
            reroll = true;
            action(1, 0, 0);
            return;
        }
        doUpgrade(newStats)
    } else if (status === 30) {
//        preview stats with premium roll
//        2 options given here:
//          0: Reroll
//          1: Upgrade!
//          if selection == 1 : status == 30
//          else status == 29: action(1, 0, undefined)
        if (!reroll) {
            slot = selection;
        }
        return preview(slot, upgradeNormal)
    } else if (status === 31) {
//        Handle weapon upgrade -- premium tier
//        cm.dispose()
        if (selection == 0) { // reroll
            status = 29;
            reroll = true;
            action(1, 0, 0);
            return;
        }
        return doUpgrade(newStats)

    } else if (status === 40) {
//        Show Materials, Mesos and NX to refund if applicable
//        Use YesNo
        slot = selection;
        return salvageSelection(selection);
    } else if (status === 41) {
        return salvageItem()
    } else if (status === 42) {
//        Handle Salvage/Refund
//        cm.dispose()
    }
}
// =========================== Main NPC Chat Sequence End ===========================

// =============================== NPC Chat Functions ===============================
function Menu() {
    var selStr = "\r\n#b#L0#Regular upgrades#l" +
                 "\r\n#b#L1#Premium upgrades#l" +
                 "\r\n#b#L2#Salvage my item!#l";
    cm.sendSimple(selStr);
}

function weaponSelection(selection) {
    if (selection === 0) {
        upgradeNormal = true;
        status = 19; // jump to Normal Upgrade Handler
    } else if (selection === 1) {
        upgradeNormal = false;
        status = 29; // jump to Permium Upgrade Handler
    } else if (selection === 2) {
        status = 39; // jump to salvage
    } else {
        cm.sendOk("Error Encountered at weapon selection. Alert GM.")
        return cm.dispose();
    }

    var inv      = cm.getInventory(1);
    var limit    = inv.getSlotLimit();
    var lines    = [];

    for (var slot = 1; slot <= limit; slot++) {
        var item = inv.getItem(slot);
        if (!item) continue;
        var name = Packages.server.ItemInformationProvider
                   .getInstance().getName(item.getItemId());
        if (cm.checkBlacklistedItem(slot) & selection === 2) { // Make sures any item they planned to salvage cant level up on their own
            continue;
        }
        lines.push(
            "#L" + slot + "#"
            + "#v" + item.getItemId() + "# "
            + name
            + " (Lv " + item.getItemLevel() + ")"
            + "#l"
        );
    }

    if (!lines.length) {
        cm.sendOk("You have no equippable items to select.");
        return cm.dispose();
    } else if (upgradeNormal) {
        cm.sendSimple(
            "Select the item you want to upgrade. "
          + "It costs Item required level / 2 to preview each upgrade.\r\n"
          + lines.join("\r\n")
        );
    } else {
        cm.sendSimple(
            "Select the item you want to upgrade. "
          + "It costs Item required level / 20 to preview each upgrade.\r\n"
          + lines.join("\r\n")
        );
    }
}

function preview(slot, upgradeNormal) {
    // All the conditional Checks
    selectedItem = cm.getInventory(1).getItem(slot);
    ii = Packages.server.ItemInformationProvider.getInstance().getEquipLevelReq(selectedItem.getItemId())

    if (!selectedItem) {
        cm.sendOk("Invalid selection.");
        return cm.dispose();
    }
    if (upgradeNormal) {
        newStats = calcNewStats(selectedItem, selectedItem.getItemId());
    } else {
        newStats = calcBetterNewStats(selectedItem, selectedItem.getItemId());
    }

    previewFee = (upgradeNormal ? ii/2 * 100000 : ii/2 * 1000000) // cost of better rol is 10x more
    var lvl   = selectedItem.getItemLevel();
    var hands = selectedItem.getHands();

    // Rebirth condition: level = 5 but and not rebirthed 3 times
    if (lvl == 5 && hands <= 2) {
        isRebirth = true;
        return cm.sendYesNo(
            "Your item has reached its max upgrades. I can reset it with a base stat boost.\r\n"
          + "Cost: 1x#v" + rockOfTime + "# + 350k NX. Proceed?"
        );
    }

    // Regular upgrade: level 1–4, hands ≤=3
    if (lvl >= 1 && lvl <= 4 && hands <= 3) {
        if (0 <= selectedItem.getHands() <= 3) { // able to configure to hand different upgradeConfigRb0-3 if needed here
            var cfg  = upgradeConfigRb0[lvl];
        }
        if (!cfg) {
            cm.sendOk("No upgrade path configured for level " + lvl + ".");
            return cm.dispose();
        }

        if (cm.getMeso() < previewFee + cfg.fee) {
            if (cm.haveItem(3020002, 1)) {
                cm.gainItem(3020002, -1)
                cm.gainMeso(1000000000);
            } else {
                cm.sendOk("You need at least "
                    + format(previewFee + cfg.fee)
                    + " mesos to preview and perform this upgrade.");
                return cm.dispose();
            }
        }

        // Deduct preview fee
        cm.gainMeso(-previewFee);

        // Calculate tentative new stats

        var mat      = cfg.mats[0].id;
        var amt      = cfg.mats[0].amt;
        var warning  = (lvl === 4)
                     ? "\r\nWARNING: 1% chance to destroy your item!"
                     : "";

        var msg = [
            "Upgrading will change stats as follows:",
            "STR: " + selectedItem.getStr() + " to " + newStats.str + " (x" + newStats.mult[0].toFixed(2) + ")",
            "DEX: " + selectedItem.getDex() + " to " + newStats.dex + " (x" + newStats.mult[1].toFixed(2) + ")",
            "INT: " + selectedItem.getInt() + " to " + newStats.int + " (x" + newStats.mult[2].toFixed(2) + ")",
            "LUK: " + selectedItem.getLuk() + " to " + newStats.luk + " (x" + newStats.mult[3].toFixed(2) + ")",
            "WATK: " + selectedItem.getWatk() + " to " + newStats.watk + " (x" + newStats.mult[4].toFixed(2) + ")",
            "MATK: " + selectedItem.getMatk() + " to " + newStats.matk + " (x" + newStats.mult[5].toFixed(2) + ")",
            "WDEF: " + selectedItem.getWdef() + " to " + newStats.wdef,
            "MDEF: " + selectedItem.getMdef() + " to " + newStats.mdef,
            "Cost: " + format(cfg.fee) + " + " + amt + "x#v" + mat + "#"
        ].join("\r\n");

        return cm.sendSimple(msg + warning + "\r\n#L0#Reroll preview stats#l\r\n#L1#Proceed with upgrade#l");
    }

    // Otherwise, nothing to do
    cm.sendOk("Your item cannot be upgraded further or is ineligible.");
    cm.dispose();
}

function calcNewStats(item, itemId) {
    // Main stats 40–60% increase, defs 10–20%
//    if (parseInt(itemId/10000) < 130) {
    var mm = () => 1.4 + Math.random() * 0.2;
    var dm = () => 1.1 + Math.random() * 0.1;
    var values = Array.from({ length: 6 }, mm);
    return {
        str:  Math.floor(item.getStr()  * values[0]),
        dex:  Math.floor(item.getDex()  * values[1]),
        int:  Math.floor(item.getInt()  * values[2]),
        luk:  Math.floor(item.getLuk()  * values[3]),
        watk: Math.floor(item.getWatk() * values[4]),
        matk: Math.floor(item.getMatk() * values[5]),
        wdef: Math.floor(item.getWdef() * dm()),
        mdef: Math.floor(item.getMdef() * dm()),
        lvl:  item.getItemLevel() + 1,
        mult: values
    };
}

function calcBetterNewStats(item, itemId) {
    // Main stats 55–60% increase, defs 10–20%
//    if (parseInt(itemId/10000) < 130) {
//        var mm = 1.4 + Math.random() * 0.2;
//    } else {
//        var mm = 1.4 + Math.random() * 0.2;
//    }
    var mm = 1.4 + Math.random() * 0.2;
//    var dm = () => 1.1 + Math.random() * 0.1;
    var dm = 1.1 + Math.random() * 0.1;
    var values = new Array(6).fill(mm);
    return {
        str:  Math.floor(item.getStr()  * values[0]),
        dex:  Math.floor(item.getDex()  * values[1]),
        int:  Math.floor(item.getInt()  * values[2]),
        luk:  Math.floor(item.getLuk()  * values[3]),
        watk: Math.floor(item.getWatk() * values[4]),
        matk: Math.floor(item.getMatk() * values[5]),
        wdef: Math.floor(item.getWdef() * dm),
        mdef: Math.floor(item.getMdef() * dm),
        lvl:  item.getItemLevel() + 1,
        mult: values
    };
}

function doUpgrade(newStats) {
    var lvl  = selectedItem.getItemLevel();
    if (lvl == 5) {
//        console.log("Rebirth")
        return doRebirth();
    }
//    console.log(selectedItem.getHands())
    if (0 <= selectedItem.getHands() <= 3) { // able to configure to hand different upgradeConfigRb0-3 if needed here
        var cfg  = upgradeConfigRb0[lvl];
    }
    var mat  = cfg.mats[0].id;
    var amt  = cfg.mats[0].amt;

    // Check materials
    if (!cm.haveItem(mat, amt)) {
        cm.sendOk("You lack " + amt + "x#v" + mat + "#.");
        return cm.dispose();
    }

    // Deduct cost & materials
    cm.gainMeso(-cfg.fee);
    cm.gainItem(mat, -amt);

    // Success roll
    var successRate = 1 - 0.1 * (lvl - 1);
    var boomChance  = (lvl === 4 ? 0.005 : 0);
    var roll        = Math.random();
    var success     = (roll < successRate);
    var boom        = (!success && Math.random() < boomChance);

    if (success) {
        applyNewStats(newStats);
        cm.sendOk("By the blessing from Carbo, your item has been upgraded successfully!");
        cm.scrollPass(cm.getPlayer().getId());
    } else if (boom) {
        if (cm.haveItem(boomProtectScroll, 1)) {
            cm.sendOk("BOOM SHAKA LA.. eh? what? AL AKAHS MOOB?!?! Huh? Did time rewind? Weird... What was I doing...");
            cm.gainItem(boomProtectScroll, -1)
        } else {
            cm.removeItemNPC(selectedItem.getPosition());
            cm.sendOk("BOOM SHAKA LAKA! BOOM BOOM BOOM~~ Your item has exploded into fireworks by Merogie!");
            cm.scrollBoom(cm.getPlayer().getId());
        }
    } else {
        cm.sendOk("Upgrade failed. Better luck next time.");
        cm.scrollFail(cm.getPlayer().getId());
    }

    return cm.dispose();
}

function applyNewStats(newStats) {
    var s = newStats;
    selectedItem.setStr(s.str);
    selectedItem.setDex(s.dex);
    selectedItem.setInt(s.int);
    selectedItem.setLuk(s.luk);
    selectedItem.setWatk(s.watk);
    selectedItem.setMatk(s.matk);
    selectedItem.setWdef(s.wdef);
    selectedItem.setMdef(s.mdef);
    selectedItem.setItemLevel(s.lvl);
    cm.getPlayer().forceUpdateItem(selectedItem);
}

function doRebirth() {
    if (selectedItem.getItemId() == 1402180 || selectedItem.getItemId() == 1382235) { // Just a double check for Kaiserium and Alicia
        cm.sendOk("Hello! Your item is already so op, you can't rebirth it!");
        cm.dispose();
        return;
    }
    // Check materials
    if (!cm.haveItem(rockOfTime, 1)) {
        cm.sendOk("You need 1x#v" + rockOfTime + "# to rebirth.");
    } else if (cm.getCashShop().getCash(1) < 350000) {
        cm.sendOk("You need 350k NX to rebirth your item.");
    } else {
        cm.rebirthItem(selectedItem.getPosition(), selectedItem.getHands());
        cm.gainItem(rockOfTime, -1);
        cm.gainCash(-350000);
        cm.scrollPass(cm.getPlayer().getId());
        cm.sendOk("Your item has been reborn. Go get stronger!");
    }
    return cm.dispose();
}

function format(n) {
    return cm.numberWithCommas(n) + " mesos";
}

function getTotals(uptoLevel, hands) {
    /*
    Function to handle the materials to refund
    Will loop through the levels and  and decide the total materials to refund for that rebrith
    */
  let totalFee = 0;
  const totalMats = {};    // { materialId: totalAmt, … }
  if (0 <= hands <= 3) { // able to configure to hand different upgradeConfigRb0-3 if needed here
    upgradeConfig = upgradeConfigRb0
  }

  // loop from 1 → uptoLevel
  for (let lvl = 1; lvl <= uptoLevel; lvl++) {
    const step = upgradeConfig[lvl-1];
    if (!step) continue;   // in case some levels are missing

    // add the fee
    totalFee += step.fee;

    // accumulate each material
    step.mats.forEach(({ id, amt }) => {
      totalMats[id] = (totalMats[id] || 0) + amt;
    });
  }

  return { totalFee, totalMats };
}

function salvageSelection(slot) {
    selectedItem = cm.getInventory(1).getItem(slot);
    if (!selectedItem) {
        cm.sendOk("Invalid selection.");
        return cm.dispose();
    }
    // nothing to salvage if level 1 and hands = 0
    if (selectedItem.getItemLevel() === 1 && selectedItem.getHands() === 0) {
        cm.sendOk("Clean item selected, nothing to salvage.");
        return cm.dispose();
    }

    const lvl        = selectedItem.getItemLevel();
    const hands      = selectedItem.getHands();
    const { totalFee, totalMats } = getTotals(lvl);

    // 1) Initialize with guaranteed returns per hand
    const matsToReturn = {};
    matsToReturn[zakDiamond] = 4 * hands;
    matsToReturn[hTegg]      = 4 * hands;

    // 2) Merge in all mats used up through this level
    Object.keys(totalMats).forEach(id => {
        const used = totalMats[id] || 0;
        matsToReturn[id] = (matsToReturn[id] || 0) + used;
    });

    // 3) Compute 20% refund of full cost (including totalUpgradeFee per hand)
    const refundMesos = Math.floor((totalFee + totalUpgradeFee * hands) * 0.2);

    // 4) Build confirmation message
    let msg = "We will refund you " + format(refundMesos) + "\r\n";
    Object.entries(matsToReturn).forEach(([id, amt]) => {
        msg += amt + "x #v" + id + "#\r\n";
    });
    if (hands > 0) {
        msg += hands + "x #v" + rockOfTime + "#\r\n";
    }
    msg += "Are you sure you want to salvage this equip?";

    cm.sendYesNo(msg);
}

function salvageItem() {
    var lvl        = selectedItem.getItemLevel();
    var hands      = selectedItem.getHands();
    var { totalFee, totalMats } = getTotals(lvl);

    // 1) Initialize with guaranteed returns per hand
    var matsToReturn = {};
    matsToReturn[zakDiamond] = 4 * hands;
    matsToReturn[hTegg]      = 4 * hands;

    // 2) Merge in all mats used up through this level
    Object.keys(totalMats).forEach(id => {
        const used = totalMats[id] || 0;
        matsToReturn[id] = (matsToReturn[id] || 0) + used;
    });

    // 3) Compute 20% refund of full cost (including totalUpgradeFee per hand)
    const refundMesos = Math.floor((totalFee + totalUpgradeFee * hands) * 0.2);

    // 4) Build confirmation message
    var returnstr = "I have salvaged your items, please check."
    cm.gainMeso(refundMesos)
    Object.entries(matsToReturn).forEach(([id, amt]) => {
        cm.gainItem(parseInt(id), amt);
    });
    cm.gainItem(rockOfTime, hands);
    cm.gainCash(350000 * hands * 0.6);
    cm.removeItemNPC(selectedItem.getPosition());
    return cm.dispose();
}
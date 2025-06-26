// Buyback NPC script
// Allows players to recover recently sold items (within 1 hour) for a 100% fee (min 10M)

var buybackItems = null;
var buybackFee = 1.0; // 100% markup
var selectedIndex = -1;
var selectedCost = 0;

var ItemInfo = Packages.server.ItemInformationProvider.getInstance();

function start() {
    buybackItems = cm.getPlayer().getBuybackItems();

    if (!buybackItems || buybackItems.length === 0) {
        cm.sendOk("No items available for buyback.");
        cm.dispose();
        return;
    }

    var text = "Select an item to buy back (100% fee applies or minimum 10M mesos):\r\n\r\n";
    for (var i = 0; i < buybackItems.length; i++) {
        var entry = buybackItems[i];
        var item = entry.getItem();
        var basePrice = entry.getPrice();
        var cost = Math.max(Math.floor(basePrice * (1 + buybackFee)), 10000000);
        var itemName = ItemInfo.getName(item.getItemId()) || ("Item " + item.getItemId());

        text += "#L" + i + "#"
              + "#v" + item.getItemId() + "# "
              + itemName + " x" + item.getQuantity()
              + " - " + cost.toLocaleString() + " mesos\r\n";
    }

    cm.sendSimple(text);
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    if (selectedIndex === -1) {
        // First step: player selected an item
        if (selection < 0 || selection >= buybackItems.length) {
            cm.sendOk("Invalid selection.");
            cm.dispose();
            return;
        }

        selectedIndex = selection;
        var entry = buybackItems[selectedIndex];
        var price = entry.getPrice();
        selectedCost = Math.max(Math.floor(price * (1 + buybackFee)), 10000000);

        if (cm.getMeso() < selectedCost) {
            cm.sendOk("You don't have enough mesos.");
            cm.dispose();
            return;
        }

        if (!cm.getPlayer().buybackItem(selectedIndex)) {
            cm.sendOk("Failed to recover the item. It may have expired or your inventory is full.");
            cm.dispose();
            return;
        }

        cm.gainMeso(-selectedCost);
        cm.sendOk("Item bought back successfully!");
        cm.dispose();
    }
}

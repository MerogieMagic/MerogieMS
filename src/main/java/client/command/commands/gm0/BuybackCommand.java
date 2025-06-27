package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import scripting.npc.NPCScriptManager;
import constants.id.NpcId;

public class BuybackCommand extends Command {
    {
        setDescription("Open the buyback interface to recover sold items.");
    }

    private static final int BUYBACK_NPC_ID = NpcId.MAPLE_ADMINISTRATOR; // Or use a custom ID if desired

    @Override
    public void execute(Client c, String[] params) {
        Character chr = c.getPlayer();

        if (chr.getTrade() != null || chr.getMiniGame() != null || chr.getPlayerShop() != null) {
            chr.dropMessage(5, "You cannot use the buyback feature while in a trade or shop.");
            return;
        }

        try {
            chr.closePlayerInteractions();

            NPCScriptManager npcManager = NPCScriptManager.getInstance();
            if (npcManager != null) {
                npcManager.dispose(c);
                Thread.sleep(100); // slight delay for cleanup
                npcManager.start(c, BUYBACK_NPC_ID, "buyback", chr);
            } else {
                chr.dropMessage(5, "Unable to access the NPC system.");
            }

        } catch (Exception e) {
            chr.dropMessage(5, "An error occurred while opening the buyback interface.");
            e.printStackTrace();
        }
    }
}

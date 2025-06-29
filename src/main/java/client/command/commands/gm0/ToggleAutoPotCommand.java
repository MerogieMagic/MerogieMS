package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;

public class ToggleAutoPotCommand extends Command {

    {
        setDescription("Toggles between MultiPot and Vanilla Pet Auto Pot.");
    }

    // ✅ NOT an override — just a helper method
    public void execute(Character chr, String[] args) {
        boolean current = chr.isAutopotEnabled();
        chr.setAutopotEnabled(!current);
        chr.dropMessage(6, "[System] Pet AutoPot has been " + (chr.isAutopotEnabled() ? "enabled" : "disabled") + ".");
    }

    // ✅ THIS is the required method
    @Override
    public void execute(Client client, String[] args) {
        execute(client.getPlayer(), args);
    }
}

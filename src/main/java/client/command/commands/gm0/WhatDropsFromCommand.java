/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import constants.id.NpcId;
import server.ItemInformationProvider;
import server.life.MonsterDropEntry;
import server.life.MonsterInformationProvider;
import tools.Pair;

import java.util.Iterator;

public class WhatDropsFromCommand extends Command {
    {
        setDescription("Show what items drop from a mob.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.dropMessage(5, "Please do @whatdropsfrom <monster name>");
            return;
        }
        String monsterName = player.getLastCommandMessage();
        String output = "";
        int limit = 3;
        Iterator<Pair<Integer, String>> listIterator = MonsterInformationProvider.getMobsIDsFromName(monsterName).iterator();
        for (int i = 0; i < limit; i++) {
            if (listIterator.hasNext()) {
                Pair<Integer, String> data = listIterator.next();
                int mobId = data.getLeft();
                String mobName = data.getRight();
                output += mobName + " drops the following items:\r\n\r\n";
                for (MonsterDropEntry drop : MonsterInformationProvider.getInstance().retrieveDrop(mobId)) {
                    try {
                        String name = ItemInformationProvider.getInstance().getName(drop.itemId);
                        if (name == null || name.equals("null") || drop.chance == 0) {
                            continue;
                        }
                        float chance = Math.max(1000000 / drop.chance / (!MonsterInformationProvider.getInstance().isBoss(mobId) ? player.getDropRate() : player.getBossDropRate()), 1);
                        output += "- " + name + " (1/" + (int) chance + ")\r\n";
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        continue;
                    }
                }
                output += "\r\n";
            }
        }

        c.getAbstractPlayerInteraction().npcTalk(NpcId.MAPLE_ADMINISTRATOR, output);
    }
}

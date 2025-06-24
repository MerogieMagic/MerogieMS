/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

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
package scripting.npc;

import client.Character;
import client.*;
import client.inventory.Equip; // Slimy Add
import client.inventory.Inventory;
import client.inventory.InventoryType; // Slimy Add
import client.inventory.Item;
import client.inventory.ItemFactory;
import client.inventory.Pet;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.game.GameConstants;
import constants.id.MapId;
import constants.id.NpcId;
import constants.inventory.ItemConstants;
import constants.string.LanguageConstants;
import net.packet.Packet;
import net.server.Server;
import net.server.channel.Channel;
import net.server.coordinator.matchchecker.MatchCheckerListenerFactory.MatchCheckerType;
import net.server.guild.Alliance;
import net.server.guild.Guild;
import net.server.guild.GuildPackets;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.Data;
import provider.DataProviderFactory;
import provider.wz.WZFiles;
import scripting.AbstractPlayerInteraction;
import server.*;
import server.SkillbookInformationProvider.SkillBookEntry;
import server.events.gm.Event;
import server.expeditions.Expedition;
import server.expeditions.ExpeditionType;
import server.gachapon.Gachapon;
import server.gachapon.Gachapon.GachaponItem;
import server.life.LifeFactory;
import server.life.PlayerNPC;
import server.maps.MapManager;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import server.partyquest.AriantColiseum;
import server.partyquest.MonsterCarnival;
import server.partyquest.Pyramid;
import server.partyquest.Pyramid.PyramidMode;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.packets.WeddingPackets;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Matze
 */
public class NPCConversationManager extends AbstractPlayerInteraction {
    private static final Logger log = LoggerFactory.getLogger(NPCConversationManager.class);

    private final int npc;
    private int npcOid;
    private String scriptName;
    private String getText;
    private boolean itemScript;
    private List<PartyCharacter> otherParty;

    private final Map<Integer, String> npcDefaultTalks = new HashMap<>();

    private String getDefaultTalk(int npcid) {
        String talk = npcDefaultTalks.get(npcid);
        if (talk == null) {
            talk = LifeFactory.getNPCDefaultTalk(npcid);
            npcDefaultTalks.put(npcid, talk);
        }

        return talk;
    }

    public NPCConversationManager(Client c, int npc, String scriptName) {
        this(c, npc, -1, scriptName, false);
    }

    public NPCConversationManager(Client c, int npc, List<PartyCharacter> otherParty, boolean test) {
        super(c);
        this.c = c;
        this.npc = npc;
        this.otherParty = otherParty;
    }

    public NPCConversationManager(Client c, int npc, int oid, String scriptName, boolean itemScript) {
        super(c);
        this.npc = npc;
        this.npcOid = oid;
        this.scriptName = scriptName;
        this.itemScript = itemScript;
    }

    public int getNpc() {
        return npc;
    }

    public int getNpcObjectId() {
        return npcOid;
    }

    public String getScriptName() {
        return scriptName;
    }

    public boolean isItemScript() {
        return itemScript;
    }

    public void resetItemScript() {
        this.itemScript = false;
    }

    public void dispose() {
        NPCScriptManager.getInstance().dispose(this);
        getClient().sendPacket(PacketCreator.enableActions());
    }

    public void sendNext(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "00 01", (byte) 0));
    }

    public void sendPrev(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "01 00", (byte) 0));
    }

    public void sendNextPrev(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "01 01", (byte) 0));
    }

    public void sendOk(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00", (byte) 0));
    }

    public void sendDefault() {
        sendOk(getDefaultTalk(npc));
    }

    public void sendYesNo(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 1, text, "", (byte) 0));
    }

    public void sendAcceptDecline(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0x0C, text, "", (byte) 0));
    }

    public void sendSimple(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 4, text, "", (byte) 0));
    }

    public void sendNext(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "00 01", speaker));
    }

    public void sendPrev(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "01 00", speaker));
    }

    public void sendNextPrev(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "01 01", speaker));
    }

    public void sendOk(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00", speaker));
    }

    public void sendYesNo(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 1, text, "", speaker));
    }

    public void sendAcceptDecline(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 0x0C, text, "", speaker));
    }

    public void sendSimple(String text, byte speaker) {
        getClient().sendPacket(PacketCreator.getNPCTalk(npc, (byte) 4, text, "", speaker));
    }

    public void sendStyle(String text, int[] styles) {
        if (styles.length > 0) {
            getClient().sendPacket(PacketCreator.getNPCTalkStyle(npc, text, styles));
        } else {    // thanks Conrad for noticing empty styles crashing players
            sendOk("Sorry, there are no options of cosmetics available for you here at the moment.");
            dispose();
        }
    }

    public void sendGetNumber(String text, int def, int min, int max) {
        getClient().sendPacket(PacketCreator.getNPCTalkNum(npc, text, def, min, max));
    }

    public void sendGetText(String text) {
        getClient().sendPacket(PacketCreator.getNPCTalkText(npc, text, ""));
    }

    public void sendGetText(String text, String def) {
        getClient().sendPacket(PacketCreator.getNPCTalkText(npc, text, def));
    }

    /*
     * 0 = ariant colliseum
     * 1 = Dojo
     * 2 = Carnival 1
     * 3 = Carnival 2
     * 4 = Ghost Ship PQ?
     * 5 = Pyramid PQ
     * 6 = Kerning Subway
     */
    public void sendDimensionalMirror(String text) {
        getClient().sendPacket(PacketCreator.getDimensionalMirror(text));
    }

    public void setGetText(String text) {
        this.getText = text;
    }

    public String getText() {
        return this.getText;
    }

    @Override
    public boolean forceStartQuest(int id) {
        return forceStartQuest(id, npc);
    }

    @Override
    public boolean forceCompleteQuest(int id) {
        return forceCompleteQuest(id, npc);
    }

    @Override
    public boolean startQuest(short id) {
        return startQuest((int) id);
    }

    @Override
    public boolean completeQuest(short id) {
        return completeQuest((int) id);
    }

    @Override
    public boolean startQuest(int id) {
        return startQuest(id, npc);
    }

    @Override
    public boolean completeQuest(int id) {
        return completeQuest(id, npc);
    }

    public int getMeso() {
        return getPlayer().getMeso();
    }

    public void gainMeso(int gain) {
        getPlayer().gainMeso(gain);
    }

    public void gainMeso(Double gain) {
        getPlayer().gainMeso(gain.intValue());
    }

    public void gainExp(int gain) {
        getPlayer().gainExp(gain, true, true);
    }

    public void sendPacket(Packet packet) {
        c.sendPacket(packet);
    }

    public void gainCash(int gain) {
        getPlayer().getCashShop().gainCash(1, gain);
        sendPacket(PacketCreator.showCash(getPlayer()));

    }

    @Override
    public void showEffect(String effect) {
        getPlayer().getMap().broadcastMessage(PacketCreator.environmentChange(effect, 3));
    }

    public void setHair(int hair) {
        getPlayer().setHair(hair);
        getPlayer().updateSingleStat(Stat.HAIR, hair);
        getPlayer().equipChanged();
    }

    public void setFace(int face) {
        getPlayer().setFace(face);
        getPlayer().updateSingleStat(Stat.FACE, face);
        getPlayer().equipChanged();
    }

    public void setSkin(int color) {
        getPlayer().setSkinColor(SkinColor.getById(color));
        getPlayer().updateSingleStat(Stat.SKIN, color);
        getPlayer().equipChanged();
    }

    public int itemQuantity(int itemid) {
        return getPlayer().getInventory(ItemConstants.getInventoryType(itemid)).countById(itemid);
    }

    public void displayGuildRanks() {
        Guild.displayGuildRanks(getClient(), npc);
    }

    public boolean canSpawnPlayerNpc(int mapid) {
        Character chr = getPlayer();
        return !YamlConfig.config.server.PLAYERNPC_AUTODEPLOY && chr.getLevel() >= chr.getMaxClassLevel() && !chr.isGM() && PlayerNPC.canSpawnPlayerNpc(chr.getName(), mapid);
    }

    public PlayerNPC getPlayerNPCByScriptid(int scriptId) {
        for (MapObject pnpcObj : getPlayer().getMap().getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Arrays.asList(MapObjectType.PLAYER_NPC))) {
            PlayerNPC pn = (PlayerNPC) pnpcObj;

            if (pn.getScriptId() == scriptId) {
                return pn;
            }
        }

        return null;
    }

    @Override
    public Party getParty() {
        return getPlayer().getParty();
    }

    @Override
    public void resetMap(int mapid) {
        getClient().getChannelServer().getMapFactory().getMap(mapid).resetReactors();
    }

    public void gainTameness(int tameness) {
        for (Pet pet : getPlayer().getPets()) {
            if (pet != null) {
                pet.gainTamenessFullness(getPlayer(), tameness, 0, 0);
            }
        }
    }

    public String getName() {
        return getPlayer().getName();
    }

    public int getGender() {
        return getPlayer().getGender();
    }

    public void changeJobById(int a) {
        getPlayer().changeJob(Job.getById(a));
    }

    public void changeJob(Job job) {
        getPlayer().changeJob(job);
    }

    public String getJobName(int id) {
        return GameConstants.getJobName(id);
    }

    public StatEffect getItemEffect(int itemId) {
        return ItemInformationProvider.getInstance().getItemEffect(itemId);
    }

    public void resetStats() {
        getPlayer().resetStats();
    }

    public void openShopNPC(int id) {
        Shop shop = ShopFactory.getInstance().getShop(id);

        if (shop != null) {
            shop.sendShop(c);
        } else {    // check for missing shopids thanks to resinate
            log.warn("Shop ID: {} is missing from database.", id);
            ShopFactory.getInstance().getShop(11000).sendShop(c);
        }
    }

    public void maxMastery() {
        for (Data skill_ : DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Skill.img").getChildren()) {
            try {
                Skill skill = SkillFactory.getSkill(Integer.parseInt(skill_.getName()));
                getPlayer().changeSkillLevel(skill, (byte) 0, skill.getMaxLevel(), -1);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
                break;
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                continue;
            }
        }
    }

    public void doGachapon() {
        doGachapon(false);
    };

    public void doGachapon(boolean isSilent) {
        GachaponItem item = Gachapon.getInstance().process(npc);
        Item itemGained = gainItem(item.getId(), (short) (item.getId() / 10000 == 200 ? 100 : 1), true, true); // For normal potions, make it give 100.

        sendNext("You have obtained a #b#t" + item.getId() + "##k.");

        int[] maps = {MapId.HENESYS, MapId.ELLINIA, MapId.PERION, MapId.KERNING_CITY, MapId.SLEEPYWOOD, MapId.MUSHROOM_SHRINE,
                MapId.SHOWA_SPA_M, MapId.SHOWA_SPA_F, MapId.LUDIBRIUM, MapId.NEW_LEAF_CITY, MapId.EL_NATH, MapId.NAUTILUS_HARBOR};
        final int mapId = maps[(getNpc() != NpcId.GACHAPON_NAUTILUS) ?
                (getNpc() - NpcId.GACHAPON_HENESYS) : 11];
        String map = c.getChannelServer().getMapFactory().getMap(mapId).getMapName();

        Gachapon.log(getPlayer(), item.getId(), map);

        if (item.getTier() > 2) { //Uncommon and Rare
        //    Server.getInstance().broadcastMessage(c.getWorld(), PacketCreator.gachaponMessage(itemGained, map, getPlayer())); Merogie -- Muted Gachapon
        }
    }

    /**
     * Custom code by Liquid
     * @param amount
     */
    public void doGachaponWithDelay(int amount) {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final AtomicInteger count = new AtomicInteger(0);

        scheduler.scheduleAtFixedRate(() -> {
            if (count.incrementAndGet() > amount || c.getPlayer() == null) {
                scheduler.shutdown();
                return;
            }

            //Remove it ticket from inventory
            this.gainItem(5220000, (short) -1);

            GachaponItem item = Gachapon.getInstance().process(npc);
            Item itemGained = gainItem(item.getId(), (short) (item.getId() / 10000 == 200 ? 100 : 1), true, true);

            int[] maps = {MapId.HENESYS, MapId.ELLINIA, MapId.PERION, MapId.KERNING_CITY, MapId.SLEEPYWOOD, MapId.MUSHROOM_SHRINE,
                    MapId.SHOWA_SPA_M, MapId.SHOWA_SPA_F, MapId.LUDIBRIUM, MapId.NEW_LEAF_CITY, MapId.EL_NATH, MapId.NAUTILUS_HARBOR};
            final int mapId = maps[(getNpc() != NpcId.GACHAPON_NAUTILUS) ?
                                   (getNpc() - NpcId.GACHAPON_HENESYS) : 11];
            String map = c.getChannelServer().getMapFactory().getMap(mapId).getMapName();

            Gachapon.log(getPlayer(), item.getId(), map);

            if (item.getTier() > 2) {
          //      Server.getInstance().broadcastMessage(c.getWorld(), PacketCreator.gachaponMessage(itemGained, map, getPlayer()));
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    public boolean hasEnoughSlotsForGachapon(int count) {
        return c.getPlayer().getInventory(InventoryType.EQUIP).getNumFreeSlot() >= count &&  // EQUIP
                c.getPlayer().getInventory(InventoryType.USE).getNumFreeSlot() >= count &&  // USE
                c.getPlayer().getInventory(InventoryType.SETUP).getNumFreeSlot() >= count &&  // SETUP
                c.getPlayer().getInventory(InventoryType.ETC).getNumFreeSlot() >= count;    // ETC
    }

    public void upgradeAlliance() {
        Alliance alliance = Server.getInstance().getAlliance(c.getPlayer().getGuild().getAllianceId());
        alliance.increaseCapacity(1);

        Server.getInstance().allianceMessage(alliance.getId(), GuildPackets.getGuildAlliances(alliance, c.getWorld()), -1, -1);
        Server.getInstance().allianceMessage(alliance.getId(), GuildPackets.allianceNotice(alliance.getId(), alliance.getNotice()), -1, -1);

        c.sendPacket(GuildPackets.updateAllianceInfo(alliance, c.getWorld()));  // thanks Vcoc for finding an alliance update to leader issue
    }

    public void disbandAlliance(Client c, int allianceId) {
        Alliance.disbandAlliance(allianceId);
    }

    public boolean canBeUsedAllianceName(String name) {
        return Alliance.canBeUsedAllianceName(name);
    }

    public Alliance createAlliance(String name) {
        return Alliance.createAlliance(getParty(), name);
    }

    public int getAllianceCapacity() {
        return Server.getInstance().getAlliance(getPlayer().getGuild().getAllianceId()).getCapacity();
    }

    public boolean hasMerchant() {
        return getPlayer().hasMerchant();
    }

    public boolean hasMerchantItems() {
        try {
            if (!ItemFactory.MERCHANT.loadItems(getPlayer().getId(), false).isEmpty()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return getPlayer().getMerchantMeso() != 0;
    }

    public void showFredrick() {
        c.sendPacket(PacketCreator.getFredrick(getPlayer()));
    }

    public int partyMembersInMap() {
        int inMap = 0;
        for (Character char2 : getPlayer().getMap().getCharacters()) {
            if (char2.getParty() == getPlayer().getParty()) {
                inMap++;
            }
        }
        return inMap;
    }

    public Event getEvent() {
        return c.getChannelServer().getEvent();
    }

    public void divideTeams() {
        if (getEvent() != null) {
            getPlayer().setTeam(getEvent().getLimit() % 2); //muhaha :D
        }
    }

    public Character getMapleCharacter(String player) {
        Character target = Server.getInstance().getWorld(c.getWorld()).getChannel(c.getChannel()).getPlayerStorage().getCharacterByName(player);
        return target;
    }

    public void logLeaf(String prize) {
        MapleLeafLogger.log(getPlayer(), true, prize);
    }

    public boolean createPyramid(String mode, boolean party) {//lol
        PyramidMode mod = PyramidMode.valueOf(mode);

        Party partyz = getPlayer().getParty();
        MapManager mapManager = c.getChannelServer().getMapFactory();

        MapleMap map = null;
        int mapid = MapId.NETTS_PYRAMID_SOLO_BASE;
        if (party) {
            mapid += 10000;
        }
        mapid += (mod.getMode() * 1000);

        for (byte b = 0; b < 5; b++) {//They cannot warp to the next map before the timer ends (:
            map = mapManager.getMap(mapid + b);
            if (map.getCharacters().size() > 0) {
                continue;
            } else {
                break;
            }
        }

        if (map == null) {
            return false;
        }

        if (!party) {
            partyz = new Party(-1, new PartyCharacter(getPlayer()));
        }
        Pyramid py = new Pyramid(partyz, mod, map.getId());
        getPlayer().setPartyQuest(py);
        py.warp(mapid);
        dispose();
        return true;
    }

    public boolean itemExists(int itemid) {
        return ItemInformationProvider.getInstance().getName(itemid) != null;
    }

    public int getCosmeticItem(int itemid) {
        if (itemExists(itemid)) {
            return itemid;
        }

        int baseid;
        if (itemid < 30000) {
            baseid = (itemid / 1000) * 1000 + (itemid % 100);
        } else {
            baseid = (itemid / 10) * 10;
        }

        return itemid != baseid && itemExists(baseid) ? baseid : -1;
    }

    private int getEquippedCosmeticid(int itemid) {
        if (itemid < 30000) {
            return getPlayer().getFace();
        } else {
            return getPlayer().getHair();
        }
    }

    public boolean isCosmeticEquipped(int itemid) {
        return getEquippedCosmeticid(itemid) == itemid;
    }

    public boolean isUsingOldPqNpcStyle() {
        return YamlConfig.config.server.USE_OLD_GMS_STYLED_PQ_NPCS && this.getPlayer().getParty() != null;
    }

    public Object[] getAvailableMasteryBooks() {
        return ItemInformationProvider.getInstance().usableMasteryBooks(this.getPlayer()).toArray();
    }

    public Object[] getAvailableSkillBooks() {
        List<Integer> ret = ItemInformationProvider.getInstance().usableSkillBooks(this.getPlayer());
        ret.addAll(SkillbookInformationProvider.getTeachableSkills(this.getPlayer()));

        return ret.toArray();
    }

    public Object[] getNamesWhoDropsItem(Integer itemId) {
        return ItemInformationProvider.getInstance().getWhoDrops(itemId).toArray();
    }

    public String getSkillBookInfo(int itemid) {
        SkillBookEntry sbe = SkillbookInformationProvider.getSkillbookAvailability(itemid);
        switch (sbe) {
            case UNAVAILABLE:
                return "";

            case REACTOR:
                return "    Obtainable through #rexploring#k (loot boxes).";

            case SCRIPT:
                return "    Obtainable through #rexploring#k (field interaction).";

            case QUEST_BOOK:
                return "    Obtainable through #rquestline#k (collecting book).";

            case QUEST_REWARD:
                return "    Obtainable through #rquestline#k (quest reward).";

            default:
                return "    Obtainable through #rquestline#k.";
        }
    }

    // (CPQ + WED wishlist) by -- Drago (Dragohe4rt)
    public int cpqCalcAvgLvl(int map) {
        int num = 0;
        int avg = 0;
        for (MapObject mmo : c.getChannelServer().getMapFactory().getMap(map).getAllPlayer()) {
            avg += ((Character) mmo).getLevel();
            num++;
        }
        avg /= num;
        return avg;
    }

    public boolean sendCPQMapLists() {
        String msg = LanguageConstants.getMessage(getPlayer(), LanguageConstants.CPQPickRoom);
        int msgLen = msg.length();
        for (int i = 0; i < 6; i++) {
            if (fieldTaken(i)) {
                if (fieldLobbied(i)) {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (Level: "  // "Carnival field" GMS-like improvement thanks to Jayd (jaydenseah)
                            + cpqCalcAvgLvl(980000100 + i * 100) + " / "
                            + getPlayerCount(980000100 + i * 100) + "x"
                            + getPlayerCount(980000100 + i * 100) + ")  #l\r\n";
                }
            } else {
                if (i >= 0 && i <= 3) {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (2x2) #l\r\n";
                } else {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (3x3) #l\r\n";
                }
            }
        }

        if (msg.length() > msgLen) {
            sendSimple(msg);
            return true;
        } else {
            return false;
        }
    }

    public boolean fieldTaken(int field) {
        if (!c.getChannelServer().canInitMonsterCarnival(true, field)) {
            return true;
        }
        if (!c.getChannelServer().getMapFactory().getMap(980000100 + field * 100).getAllPlayer().isEmpty()) {
            return true;
        }
        if (!c.getChannelServer().getMapFactory().getMap(980000101 + field * 100).getAllPlayer().isEmpty()) {
            return true;
        }
        return !c.getChannelServer().getMapFactory().getMap(980000102 + field * 100).getAllPlayer().isEmpty();
    }

    public boolean fieldLobbied(int field) {
        return !c.getChannelServer().getMapFactory().getMap(980000100 + field * 100).getAllPlayer().isEmpty();
    }

    public void cpqLobby(int field) {
        try {
            final MapleMap map, mapExit;
            Channel cs = c.getChannelServer();

            map = cs.getMapFactory().getMap(980000100 + 100 * field);
            mapExit = cs.getMapFactory().getMap(980000000);
            for (PartyCharacter mpc : c.getPlayer().getParty().getMembers()) {
                final Character mc = mpc.getPlayer();
                if (mc != null) {
                    mc.setChallenged(false);
                    mc.changeMap(map, map.getPortal(0));
                    mc.sendPacket(PacketCreator.serverNotice(6, LanguageConstants.getMessage(mc, LanguageConstants.CPQEntryLobby)));
                    TimerManager tMan = TimerManager.getInstance();
                    tMan.schedule(() -> mapClock((int) MINUTES.toSeconds(3)), 1500);

                    mc.setCpqTimer(TimerManager.getInstance().schedule(() -> mc.changeMap(mapExit, mapExit.getPortal(0)), MINUTES.toMillis(3)));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Character getChrById(int id) {
        return c.getChannelServer().getPlayerStorage().getCharacterById(id);
    }

    public void cancelCPQLobby() {
        for (PartyCharacter mpc : c.getPlayer().getParty().getMembers()) {
            Character mc = mpc.getPlayer();
            if (mc != null) {
                mc.clearCpqTimer();
            }
        }
    }

    private void warpoutCPQLobby(MapleMap lobbyMap) {
        MapleMap out = lobbyMap.getChannelServer().getMapFactory().getMap((lobbyMap.getId() < 980030000) ? 980000000 : 980030000);
        for (Character mc : lobbyMap.getAllPlayers()) {
            mc.resetCP();
            mc.setTeam(-1);
            mc.setMonsterCarnival(null);
            mc.changeMap(out, out.getPortal(0));
        }
    }

    private int isCPQParty(MapleMap lobby, Party party) {
        int cpqMinLvl, cpqMaxLvl;

        if (lobby.isCPQLobby()) {
            cpqMinLvl = 30;
            cpqMaxLvl = 255; // changed for higher CPQ lvl
        } else {
            cpqMinLvl = 51;
            cpqMaxLvl = 255;
        }

        List<PartyCharacter> partyMembers = party.getPartyMembers();
        for (PartyCharacter pchr : partyMembers) {
            if (pchr.getLevel() >= cpqMinLvl && pchr.getLevel() <= cpqMaxLvl) {
                if (lobby.getCharacterById(pchr.getId()) == null) {
                    return 1;  // party member detected out of area
                }
            } else {
//                System.out.println("Party Members dont fit requirements");
                return 2;  // party member doesn't fit requirements
            }
        }

        return 0;
    }

    private int canStartCPQ(MapleMap lobby, Party party, Party challenger) {
        int ret = isCPQParty(lobby, party);
        System.out.println("isCPQParty: " + ret);
        if (ret != 0) {
            return ret;
        }

        ret = isCPQParty(lobby, challenger);
        if (ret != 0) {
            return -ret;
        }

        return 0;
    }

    public void startCPQ(final Character challenger, final int field) {
        try {
            cancelCPQLobby();

            final MapleMap lobbyMap = getPlayer().getMap();
            if (challenger != null) {
                if (challenger.getParty() == null) {
                    throw new RuntimeException("No opponent found!");
                }

                for (PartyCharacter mpc : challenger.getParty().getMembers()) {
                    Character mc = mpc.getPlayer();
                    if (mc != null) {
                        mc.changeMap(lobbyMap, lobbyMap.getPortal(0));
                        TimerManager tMan = TimerManager.getInstance();
                        tMan.schedule(() -> mapClock(10), 1500);
                    }
                }
                for (PartyCharacter mpc : getPlayer().getParty().getMembers()) {
                    Character mc = mpc.getPlayer();
                    if (mc != null) {
                        TimerManager tMan = TimerManager.getInstance();
                        tMan.schedule(() -> mapClock(10), 1500);
                    }
                }
            }
            final int mapid = c.getPlayer().getMapId() + 1;
            TimerManager tMan = TimerManager.getInstance();
            tMan.schedule(() -> {
                try {
                    for (PartyCharacter mpc : getPlayer().getParty().getMembers()) {
                        Character mc = mpc.getPlayer();
                        if (mc != null) {
                            mc.setMonsterCarnival(null);
                        }
                    }
                    for (PartyCharacter mpc : challenger.getParty().getMembers()) {
                        Character mc = mpc.getPlayer();
                        if (mc != null) {
                            mc.setMonsterCarnival(null);
                        }
                    }
                } catch (NullPointerException npe) {
                    warpoutCPQLobby(lobbyMap);
                    return;
                }

                Party lobbyParty = getPlayer().getParty(), challengerParty = challenger.getParty();
                int status = canStartCPQ(lobbyMap, lobbyParty, challengerParty);
                if (status == 0) {
                    new MonsterCarnival(lobbyParty, challengerParty, mapid, true, (field / 100) % 10);
                } else {
                    warpoutCPQLobby(lobbyMap);
                }
            }, 11000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startCPQ2(final Character challenger, final int field) {
        try {
            cancelCPQLobby();

            final MapleMap lobbyMap = getPlayer().getMap();
            if (challenger != null) {
                if (challenger.getParty() == null) {
                    throw new RuntimeException("No opponent found!");
                }

                for (PartyCharacter mpc : challenger.getParty().getMembers()) {
                    Character mc = mpc.getPlayer();
                    if (mc != null) {
                        mc.changeMap(lobbyMap, lobbyMap.getPortal(0));
                        mapClock(10);
                    }
                }
            }
            final int mapid = c.getPlayer().getMapId() + 100;
            TimerManager tMan = TimerManager.getInstance();
            tMan.schedule(() -> {
                try {
                    for (PartyCharacter mpc : getPlayer().getParty().getMembers()) {
                        Character mc = mpc.getPlayer();
                        if (mc != null) {
                            mc.setMonsterCarnival(null);
                        }
                    }
                    for (PartyCharacter mpc : challenger.getParty().getMembers()) {
                        Character mc = mpc.getPlayer();
                        if (mc != null) {
                            mc.setMonsterCarnival(null);
                        }
                    }
                } catch (NullPointerException npe) {
                    warpoutCPQLobby(lobbyMap);
                    return;
                }

                Party lobbyParty = getPlayer().getParty(), challengerParty = challenger.getParty();
                int status = canStartCPQ(lobbyMap, lobbyParty, challengerParty);
                if (status == 0) {
                    new MonsterCarnival(lobbyParty, challengerParty, mapid, false, (field / 1000) % 10);
                } else {
                    warpoutCPQLobby(lobbyMap);
                }
            }, 10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean sendCPQMapLists2() {
        String msg = LanguageConstants.getMessage(getPlayer(), LanguageConstants.CPQPickRoom);
        int msgLen = msg.length();
        for (int i = 0; i < 3; i++) {
            if (fieldTaken2(i)) {
                if (fieldLobbied2(i)) {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (Level: "  // "Carnival field" GMS-like improvement thanks to Jayd
                            + cpqCalcAvgLvl(980031000 + i * 1000) + " / "
                            + getPlayerCount(980031000 + i * 1000) + "x"
                            + getPlayerCount(980031000 + i * 1000) + ")  #l\r\n";
                }
            } else {
                if (i == 0 || i == 1) {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (2x2) #l\r\n";
                } else {
                    msg += "#b#L" + i + "#Carnival Field " + (i + 1) + " (3x3) #l\r\n";
                }
            }
        }

        if (msg.length() > msgLen) {
            sendSimple(msg);
            return true;
        } else {
            return false;
        }
    }

    public boolean fieldTaken2(int field) {
        if (!c.getChannelServer().canInitMonsterCarnival(false, field)) {
            return true;
        }
        if (!c.getChannelServer().getMapFactory().getMap(980031000 + field * 1000).getAllPlayer().isEmpty()) {
            return true;
        }
        if (!c.getChannelServer().getMapFactory().getMap(980031100 + field * 1000).getAllPlayer().isEmpty()) {
            return true;
        }
        return !c.getChannelServer().getMapFactory().getMap(980031200 + field * 1000).getAllPlayer().isEmpty();
    }

    public boolean fieldLobbied2(int field) {
        return !c.getChannelServer().getMapFactory().getMap(980031000 + field * 1000).getAllPlayer().isEmpty();
    }

    public void cpqLobby2(int field) {
        try {
            final MapleMap map, mapExit;
            Channel cs = c.getChannelServer();

            mapExit = cs.getMapFactory().getMap(980030000);
            map = cs.getMapFactory().getMap(980031000 + 1000 * field);
            for (PartyCharacter mpc : c.getPlayer().getParty().getMembers()) {
                final Character mc = mpc.getPlayer();
                if (mc != null) {
                    mc.setChallenged(false);
                    mc.changeMap(map, map.getPortal(0));
                    mc.sendPacket(PacketCreator.serverNotice(6, LanguageConstants.getMessage(mc, LanguageConstants.CPQEntryLobby)));
                    TimerManager tMan = TimerManager.getInstance();
                    tMan.schedule(() -> mapClock((int) MINUTES.toSeconds(3)), 1500);

                    mc.setCpqTimer(TimerManager.getInstance().schedule(() -> mc.changeMap(mapExit, mapExit.getPortal(0)), MINUTES.toMillis(3)));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void mapClock(int time) {
        getPlayer().getMap().broadcastMessage(PacketCreator.getClock(time));
    }

    private boolean sendCPQChallenge(String cpqType, int leaderid) {
        Set<Integer> cpqLeaders = new HashSet<>();
        cpqLeaders.add(leaderid);
        cpqLeaders.add(getPlayer().getId());

        return c.getWorldServer().getMatchCheckerCoordinator().createMatchConfirmation(MatchCheckerType.CPQ_CHALLENGE, c.getWorld(), getPlayer().getId(), cpqLeaders, cpqType);
    }

    public void answerCPQChallenge(boolean accept) {
        c.getWorldServer().getMatchCheckerCoordinator().answerMatchConfirmation(getPlayer().getId(), accept);
    }

    public void challengeParty2(int field) {
        Character leader = null;
        MapleMap map = c.getChannelServer().getMapFactory().getMap(980031000 + 1000 * field);
        for (MapObject mmo : map.getAllPlayer()) {
            Character mc = (Character) mmo;
            if (mc.getParty() == null) {
                sendOk(LanguageConstants.getMessage(mc, LanguageConstants.CPQFindError));
                return;
            }
            if (mc.getParty().getLeader().getId() == mc.getId()) {
                leader = mc;
                break;
            }
        }
        if (leader != null) {
            if (!leader.isChallenged()) {
                if (!sendCPQChallenge("cpq2", leader.getId())) {
                    sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQChallengeRoomAnswer));
                }
            } else {
                sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQChallengeRoomAnswer));
            }
        } else {
            sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQLeaderNotFound));
        }
    }

    public void challengeParty(int field) {
        Character leader = null;
        MapleMap map = c.getChannelServer().getMapFactory().getMap(980000100 + 100 * field);
        if (map.getAllPlayer().size() != getPlayer().getParty().getMembers().size()) {
            sendOk("An unexpected error regarding the other party has occurred.");
            return;
        }
        for (MapObject mmo : map.getAllPlayer()) {
            Character mc = (Character) mmo;
            if (mc.getParty() == null) {
                sendOk(LanguageConstants.getMessage(mc, LanguageConstants.CPQFindError));
                return;
            }
            if (mc.getParty().getLeader().getId() == mc.getId()) {
                leader = mc;
                break;
            }
        }
        if (leader != null) {
            if (!leader.isChallenged()) {
                if (!sendCPQChallenge("cpq1", leader.getId())) {
                    sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQChallengeRoomAnswer));
                }
            } else {
                sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQChallengeRoomAnswer));
            }
        } else {
            sendOk(LanguageConstants.getMessage(leader, LanguageConstants.CPQLeaderNotFound));
        }
    }

    private synchronized boolean setupAriantBattle(Expedition exped, int mapid) {
        MapleMap arenaMap = this.getMap().getChannelServer().getMapFactory().getMap(mapid + 1);
        if (!arenaMap.getAllPlayers().isEmpty()) {
            return false;
        }

        new AriantColiseum(arenaMap, exped);
        return true;
    }

    public String startAriantBattle(ExpeditionType expedType, int mapid) {
        if (!GameConstants.isAriantColiseumLobby(mapid)) {
            return "You cannot start an Ariant tournament from outside the Battle Arena Entrance.";
        }

        Expedition exped = this.getMap().getChannelServer().getExpedition(expedType);
        if (exped == null) {
            return "Please register on an expedition before attempting to start an Ariant tournament.";
        }

        List<Character> players = exped.getActiveMembers();

        int playersSize = players.size();
        if (!(playersSize >= exped.getMinSize() && playersSize <= exped.getMaxSize())) {
            return "Make sure there are between #r" + exped.getMinSize() + " ~ " + exped.getMaxSize() + " players#k in this room to start the battle.";
        }

        MapleMap leaderMap = this.getMap();
        for (Character mc : players) {
            if (mc.getMap() != leaderMap) {
                return "All competing players should be on this area to start the battle.";
            }

            if (mc.getParty() != null) {
                return "All competing players must not be on a party to start the battle.";
            }

            int level = mc.getLevel();
            if (!(level >= expedType.getMinLevel() && level <= expedType.getMaxLevel())) {
                return "There are competing players outside of the acceptable level range in this room. All players must be on #blevel between 20~30#k to start the battle.";
            }
        }

        if (setupAriantBattle(exped, mapid)) {
            return "";
        } else {
            return "Other players are already competing on the Ariant tournament in this room. Please wait a while until the arena becomes available again.";
        }
    }

    public void sendMarriageWishlist(boolean groom) {
        Character player = this.getPlayer();
        Marriage marriage = player.getMarriageInstance();
        if (marriage != null) {
            int cid = marriage.getIntProperty(groom ? "groomId" : "brideId");
            Character chr = marriage.getPlayerById(cid);
            if (chr != null) {
                if (chr.getId() == player.getId()) {
                    player.sendPacket(WeddingPackets.onWeddingGiftResult((byte) 0xA, marriage.getWishlistItems(groom), marriage.getGiftItems(player.getClient(), groom)));
                } else {
                    marriage.setIntProperty("wishlistSelection", groom ? 0 : 1);
                    player.sendPacket(WeddingPackets.onWeddingGiftResult((byte) 0x09, marriage.getWishlistItems(groom), marriage.getGiftItems(player.getClient(), groom)));
                }
            }
        }
    }

    public void sendMarriageGifts(List<Item> gifts) {
        this.getPlayer().sendPacket(WeddingPackets.onWeddingGiftResult((byte) 0xA, Collections.singletonList(""), gifts));
    }

    public boolean createMarriageWishlist() {
        Marriage marriage = this.getPlayer().getMarriageInstance();
        if (marriage != null) {
            Boolean groom = marriage.isMarriageGroom(this.getPlayer());
            if (groom != null) {
                String wlKey;
                if (groom) {
                    wlKey = "groomWishlist";
                } else {
                    wlKey = "brideWishlist";
                }

                if (marriage.getProperty(wlKey).contentEquals("")) {
                    getClient().sendPacket(WeddingPackets.sendWishList());
                    return true;
                }
            }
        }

        return false;
    }
//  ============================================================== Subordinate Helper Functions ==============================================================
    public void removeItemNPC(short itemSlot) {
        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        InventoryManipulator.removeFromSlot(this.getClient(), InventoryType.EQUIP, itemSlot, (short) 1, false); //remove the item
    }
    public boolean checkBlacklistedItem(short slot) {
        return getItemName(slot).contains("Reverse") || getItemName(slot).contains("Timeless") ||
               getItemName(slot).contains("Fearless") || getItemName(slot).contains("Balrog's Fur Shoes");
    }

    public void replaceBoomedUpgradeItem(short boomedItemSlot) {
        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        // get a clean item from the selected item ID
        int newItemId = eqpInv.getItem(boomedItemSlot).getItemId();

        eqpInv.removeSlot(boomedItemSlot); //remove the item

        //get the next free slot so we know where it's going to be placed by gainItem, for later modification
        short newItemSlot = eqpInv.getNextFreeSlot();
        this.gainItem(newItemId, (short) 1, true);

        //get the new item so we can change it
        Equip newItem = (Equip) eqpInv.getItem(newItemSlot);

        //change the stats and force update the item
        newItem.setStr((short) ((newItem.getStr() != 0) ? newItem.getStr() + 5 : 0));
        newItem.setDex((short) ((newItem.getDex() != 0) ? newItem.getDex() + 5 : 0));
        newItem.setInt((short) ((newItem.getInt() != 0) ? newItem.getInt() + 5 : 0));
        newItem.setLuk((short) ((newItem.getLuk() != 0) ? newItem.getLuk() + 5 : 0));
        newItem.setWatk((short) ((newItem.getWatk() != 0) ? newItem.getWatk() + 5 : 0));
        newItem.setMatk((short) ((newItem.getMatk() != 0) ? newItem.getMatk() + 5 : 0));
        newItem.setWdef((short) ((newItem.getWdef() != 0) ? newItem.getWdef() + 5 : 0));
        newItem.setMdef((short) ((newItem.getMdef() != 0) ? newItem.getMdef() + 5 : 0));
        this.getPlayer().forceUpdateItem(newItem);
    }

    public void rebirthItem(short ItemSlot, short hands) {
        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        Equip selectedItem = (Equip) eqpInv.getItem(ItemSlot);

        // get a clean item from the selected item ID
        int newItemId = eqpInv.getItem(ItemSlot).getItemId();
        int newItemType = newItemId / 10000;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        removeItemNPC(ItemSlot); //remove the item

        //get the next free slot so we know where it's going to be placed by gainItem, for later modification
        short newItemSlot = eqpInv.getNextFreeSlot();
        this.gainItem(newItemId, (short) 1, true);

        //get the new item so we can change it
        Equip newItem = (Equip) eqpInv.getItem(newItemSlot);
        int itemReqLevel = ii.getEquipLevelReq(newItemId);

        short addStr = 0;
        short addDex = 0;
        short addInt = 0;
        short addLuk = 0;
        short addWatk = 0;
        short addMatk = 0;

        if (newItemType >= 130) { // check if item is a weapon
            if (hands < 3) { // if item has not reached 3rb
                itemReqLevel = Math.min(itemReqLevel, 150); // locks the itemReqLevel to max 150 for watk/matk calculation
                // checks if is mage weapon or attack weapon
                if (Objects.equals(getWeaponType(newItemId), "Staff") || Objects.equals(getWeaponType(newItemId), "Wand")) {
                    addMatk = (short) Math.min(550, ((((itemReqLevel + 100) * 3) - newItem.getMatk()) / 3 * (hands + 1)));
                } else {
                    addWatk = (short) Math.min(450, (((itemReqLevel * 3) - newItem.getWatk()) / 3 * (hands + 1)));
                }

                addStr = (short) (newItem.getStr() + itemReqLevel / 3 * (hands + 1));
                addDex = (short) (newItem.getDex() + itemReqLevel / 3 * (hands + 1));
                addInt = (short) (newItem.getInt() + itemReqLevel / 3 * (hands + 1));
                addLuk = (short) (newItem.getLuk() + itemReqLevel / 3 * (hands + 1));

            } else { // if rebirth is greater than 3 (Unused/for future)
                System.out.println("Rebirth 4 and above check [in NPCConversationManager/rebirthItem");

                double carryOver = 0.25;

                addStr = (short) (selectedItem.getStr() * carryOver);
                addDex = (short) (selectedItem.getDex() * carryOver);
                addInt = (short) (selectedItem.getInt() * carryOver);
                addLuk = (short) (selectedItem.getLuk() * carryOver);
                addMatk = (short) (selectedItem.getMatk() * carryOver);
                addWatk = (short) (selectedItem.getWatk() * carryOver);
            }
        } else { // if item is not weapon (armours and accessories)

            double carryOver = 0.25; //Increment -- 25% of total stats

            addStr = (short) (selectedItem.getStr() * carryOver);
            addDex = (short) (selectedItem.getDex() * carryOver);
            addInt = (short) (selectedItem.getInt() * carryOver);
            addLuk = (short) (selectedItem.getLuk() * carryOver);
            addWatk = (short) (selectedItem.getWatk() * carryOver); // hard cap the gain such that the new Watk does not exceed 300
            addMatk = (short) (selectedItem.getMatk() * carryOver); // hard cap the gain such that the new Matk does not exceed 300
        }

        //change the stats and force update the item
        newItem.setStr((short) (newItem.getStr() + addStr));
        newItem.setDex((short) (newItem.getDex() + addDex));
        newItem.setInt((short) (newItem.getInt() + addInt));
        newItem.setLuk((short) (newItem.getLuk() + addLuk));
        newItem.setWatk((short) (newItem.getWatk() + addWatk));
        newItem.setMatk((short) (newItem.getMatk() + addMatk));
        newItem.setWdef((short) ((selectedItem.getWdef() != 0) ? newItem.getWdef() + (60 * (hands + 1)) : 0));
        newItem.setMdef((short) ((selectedItem.getMdef() != 0) ? newItem.getMdef() + (60 * (hands + 1)) : 0));
        newItem.setHands((short) (hands + 1));
        this.getPlayer().forceUpdateItem(newItem);


        //change the stats and force update the item
//        newItem.setStr((newItem.getStr() != 0) ? (short) (newItem.getStr() + addStr) : 0);
//        newItem.setDex((newItem.getDex() != 0) ? (short) (newItem.getDex() + addDex) : 0);
//        newItem.setInt((newItem.getInt() != 0) ? (short) (newItem.getInt() + addInt) : 0);
//        newItem.setLuk((newItem.getLuk() != 0) ? (short) (newItem.getLuk() + addLuk) : 0);
//        newItem.setWatk((short) ((newItem.getWatk() != 0) ? newItem.getWatk() + addWatk: 0));
//        newItem.setMatk((short) ((newItem.getMatk() != 0) ? newItem.getMatk() + addMatk : 0));
//        newItem.setWdef((short) ((newItem.getWdef() != 0) ? newItem.getWdef() + ( 60 * (hands + 1)) : 0));
//        newItem.setMdef((short) ((newItem.getMdef() != 0) ? newItem.getMdef() + ( 60 * (hands + 1)) : 0));
//        newItem.setHands((short) (hands + 1));
        this.getPlayer().forceUpdateItem(newItem);

    }

    public void bugItemHandler(short ItemSlot) {
        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        Equip selectedItem = (Equip) eqpInv.getItem(ItemSlot);

        // get a clean item from the selected item ID
        int newItemId = eqpInv.getItem(ItemSlot).getItemId();
        int newItemType = newItemId / 10000;
        int hands = selectedItem.getHands();
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        removeItemNPC(ItemSlot); //remove the item

        //get the next free slot so we know where it's going to be placed by gainItem, for later modification
        short newItemSlot = eqpInv.getNextFreeSlot();
        this.gainItem(newItemId, (short) 1, true);

        Equip newItem = (Equip) eqpInv.getItem(newItemSlot);
        int itemReqLevel = ii.getEquipLevelReq(newItemId);

        short newStr = 0;
        short newDex = 0;
        short newInt = 0;
        short newLuk = 0;
        short addWatk = 0;
        short addMatk = 0;

        if (newItemType >= 130) {
            newStr = (short) (newItem.getStr() + 60 * hands);
            newDex = (short) (newItem.getDex() + 60 * hands);
            newInt = (short) (newItem.getInt() + 60 * hands);
            newLuk = (short) (newItem.getLuk() + 60 * hands);
            addWatk = (short) (Math.min(550, (((itemReqLevel * 3)) - newItem.getWatk()) / 3 * (hands)));
            addMatk = (short) (Math.min(550, ((((itemReqLevel + 100) * 3) - newItem.getMatk()) / 3 * (hands))));
        } else {
            newStr = (short) (selectedItem.getStr() * Math.pow(1.5, selectedItem.getItemLevel() - 1) * Math.pow(0.15, selectedItem.getHands()));
            newDex = (short) (selectedItem.getDex() * Math.pow(1.5, selectedItem.getItemLevel() - 1) * Math.pow(0.15, selectedItem.getHands()));
            newInt = (short) (selectedItem.getInt() * Math.pow(1.5, selectedItem.getItemLevel() - 1) * Math.pow(0.15, selectedItem.getHands()));
            newLuk = (short) (selectedItem.getLuk() * Math.pow(1.5, selectedItem.getItemLevel() - 1) * Math.pow(0.15, selectedItem.getHands()));
            addWatk = (short) Math.min(2000 - newItem.getWatk(), (selectedItem.getWatk() * Math.pow(0.15, selectedItem.getHands()))); // hard cap the gain such that the new Watk does not exceed 2000
            addMatk = (short) Math.min(2000 - newItem.getMatk(), (selectedItem.getMatk() * Math.pow(0.15, selectedItem.getHands()))); // hard cap the gain such that the new Matk does not exceed 2000
        }

        addWatk = (short) Math.min(addWatk, 2000);
        addMatk = (short) Math.min(addMatk, 2000);

        newItem.setStr(newStr);
        newItem.setDex(newDex);
        newItem.setInt(newInt);
        newItem.setLuk(newLuk);
        newItem.setWatk((short) (newItem.getWatk() + addWatk));
        newItem.setMatk((short) (newItem.getMatk() + addMatk));
        newItem.setHands(selectedItem.getHands());
        newItem.setItemLevel(selectedItem.getItemLevel());
        this.getPlayer().forceUpdateItem(newItem);
    }

    public static String getWeaponType(int itemId) {
        int prefix = itemId / 10000;
        switch (prefix) {
            case 130:
                return "One-Handed Sword";
            case 131:
                return "One-Handed Axe";
            case 132:
                return "One-Handed Mace";
            case 133:
                return "Dagger";
            case 137:
                return "Wand";
            case 138:
                return "Staff";
            case 140:
                return "Two-Handed Sword";
            case 141:
                return "Two-Handed Axe";
            case 142:
                return "Two-Handed Mace";
            case 143:
                return "Spear";
            case 144:
                return "Pole Arm";
            case 145:
                return "Bow";
            case 146:
                return "Crossbow";
            case 147:
                return "Claw";
            case 148:
                return "Knuckle";
            case 149:
                return "Gun";
            default:
                return "Unknown";
        }
    }

    public void scrollPass(int chr) {
        sendPacket(PacketCreator.getScrollEffect(chr, Equip.ScrollResult.SUCCESS, false, false));
    }

    public void scrollFail(int chr) {
        sendPacket(PacketCreator.getScrollEffect(chr, Equip.ScrollResult.FAIL, false, false));
    }

    public void scrollBoom(int chr) {
        sendPacket(PacketCreator.getScrollEffect(chr, Equip.ScrollResult.CURSE, false, false));
    }

    public String getItemName(short ItemSlot) {
        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        Equip selectedItem = (Equip) eqpInv.getItem(ItemSlot);
        int ItemId = eqpInv.getItem(ItemSlot).getItemId();
        return ItemInformationProvider.getInstance().getName(ItemId);
    }

    // For sell command, to sell 1 item at a time
    public int SellItemSlot(short slot) {
//        Inventory eqpInv = this.getPlayer().getInventory(InventoryType.EQUIP);
        var mesosgain = 0;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int mesos = getPlayer().standaloneSell(getClient(), ii, InventoryType.EQUIP, slot, (short) 1);
//        System.out.println("Sold Item at: " + slot + " for " + mesos);
        return mesos;
    }

    public void openShop(Client c, String[] params) {
        ShopFactory.getInstance().getShop(1343).sendShop(c);
    }

    public int getBuffProgress(String buffId) {
        int progress = 0;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT progress FROM buff_donations WHERE buff_type_id = ?")) {
            ps.setString(1, buffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    progress = rs.getInt("progress");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return progress;
    }

    public int updateBuffProgress(String buffId, int amount) {
        int newProgress = 0;
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            // Insert row if it doesn't exist
            try (PreparedStatement insertIfMissing = con.prepareStatement(
                    "INSERT INTO buff_donations (buff_type_id, progress) VALUES (?, 0) ON DUPLICATE KEY UPDATE buff_type_id = buff_type_id")) {
                insertIfMissing.setString(1, buffId);
                insertIfMissing.executeUpdate();
            }

            // Update progress
            try (PreparedStatement update = con.prepareStatement(
                    "UPDATE buff_donations SET progress = progress + ? WHERE buff_type_id = ?")) {
                update.setInt(1, amount);
                update.setString(2, buffId);
                update.executeUpdate();
            }

            // Retrieve new progress
            try (PreparedStatement select = con.prepareStatement(
                    "SELECT progress FROM buff_donations WHERE buff_type_id = ?")) {
                select.setString(1, buffId);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        newProgress = rs.getInt("progress");
                    }
                }
            }

            con.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return newProgress;
    }

    public void resetBuffProgress(String buffId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE buff_donations SET progress = 0 WHERE buff_type_id = ?")) {
            ps.setString(1, buffId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void applyGlobalBuff(List<Integer> skillIds) {
        for (Channel ch : Server.getInstance().getChannelsFromWorld(getPlayer().getWorld())) {
            for (Character chr : ch.getPlayerStorage().getAllCharacters()) {
                for (int skillId : skillIds) {
                    Skill skill = SkillFactory.getSkill(skillId);
                    if (skill != null) {
                        skill.getEffect(skill.getMaxLevel()).applyTo(chr, true);
                    }
                }
            }
        }
    }

    public void broadcastWorldMessage(int type, String message) {
        Server.getInstance().broadcastMessage(getPlayer().getWorld(),
                PacketCreator.serverNotice(type, message));
    }

    // ============================== For Custom TP NPC ==============================
    public int getAccountIdByCharacterName(String characterName) {
        int accountId = -1;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT accountid FROM cosmic.characters WHERE name = ?")) {

            ps.setString(1, characterName);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    accountId = rs.getInt("accountid");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return accountId;
    }

    public List<Integer> getSavedMaps(int accountId) {
        List<Integer> maps = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT DISTINCT mapid FROM cosmic.tp_locations WHERE accountid = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    maps.add(rs.getInt("mapid"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return maps;
    }

    public boolean saveCurrentMap(int accountId, int mapId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO cosmic.tp_locations (accountid, mapid) VALUES (?, ?)")) {
            ps.setInt(1, accountId);
            ps.setInt(2, mapId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public int getMapLimit(int accountId) {
        int limit = 10;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT tplimit FROM cosmic.tp_limits WHERE accountid = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    limit = rs.getInt("tplimit");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return limit;
    }
    public void increaseMapLimit(int accountId, int increment) {
        int currentLimit = getMapLimit(accountId);
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO cosmic.tp_limits (accountid, tplimit) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE tplimit = tplimit + ?")) {
            ps.setInt(1, accountId);
            ps.setInt(2, currentLimit + increment); // Default insert value
            ps.setInt(3, increment);      // Increment value for update
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void removeSavedMap(int accountId, int mapId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM cosmic.tp_locations WHERE accountid = ? AND mapid = ?")) {
            ps.setInt(1, accountId);
            ps.setInt(2, mapId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getMapName(int mapId) {
        String map = getMap().getMapName();
        return map;
    }

    public void sendCashNoti(String text) {
        getPlayer().sendPacket(PacketCreator.earnTitleMessage(text));
    }

    public void convertItemFlag(short item) {

    }
}
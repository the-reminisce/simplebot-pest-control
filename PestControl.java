package me.remie.xeros.pestcontrol;

import simple.api.actions.SimpleObjectActions;
import simple.api.coords.WorldArea;
import simple.api.coords.WorldPoint;
import simple.api.events.ChatMessageEvent;
import simple.api.listeners.SimpleMessageListener;
import simple.api.script.Category;
import simple.api.script.Script;
import simple.api.script.ScriptManifest;
import simple.api.script.interfaces.SimplePaintable;
import simple.api.wrappers.SimpleGameObject;
import simple.api.wrappers.SimpleNpc;

import java.awt.*;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Seth on October 10/17/2021, 2021 at 3:00 PM
 *
 * @author Seth Davis <sethdavis321@gmail.com>
 * @Discord Reminisce#1707
 */
@ScriptManifest(author = "Reminisce", category = Category.MINIGAMES,
        description = "Mindlessly does pest control", name = "SPest control", version = "1.0", discord = "Reminisce#1707", servers = { "Xeros" })
public class PestControl extends Script implements SimplePaintable, SimpleMessageListener {

    private String status;
    private long startTime;

    private int gainedPoints;

    public boolean islandStarted;
    public Portal currentPortal;
    public boolean[] deadPortals = new boolean[4];

    private Random random = new Random();

    private final WorldPoint dockTile = new WorldPoint(2657, 2639, 0);
    private final WorldPoint boatTile = new WorldPoint(2661, 2639, 0);

    private final WorldArea OUTPOST_AREA = new WorldArea(
            new WorldPoint(2630, 2680, 0), new WorldPoint(2681, 2635, 0)
    );
    private final WorldArea PEST_CONTROL_AREA = new WorldArea(
            new WorldPoint(2623, 2621, 0), new WorldPoint(2689, 2560, 0)
    );

    @Override
    public boolean onExecute() {
        startTime = System.currentTimeMillis();
        status = "Waiting to start...";
        ctx.log("Thanks for using %s!", getName());
        return true;
    }

    @Override
    public void onProcess() {
        if (ctx.pathing.inArea(OUTPOST_AREA)) {
            if (islandStarted) {
                islandStarted = false;
            }
            if (ctx.pathing.distanceTo(dockTile) > 3) {
                status = "Walking to plank";
                ctx.pathing.step(dockTile);
            } else {
                if (ctx.pathing.reachable(dockTile)) {
                    final SimpleGameObject gangplank = (SimpleGameObject) ctx.objects.populate().filter(14315).nearest().next();
                    if (gangplank != null) {
                        status = "Crossing plank";
                        gangplank.interact(SimpleObjectActions.FIRST);
                        ctx.onCondition(() -> ctx.pathing.reachable(boatTile), 250, 10);
                    }
                }
            }
        } else if (ctx.pathing.inArea(PEST_CONTROL_AREA)) {
            if (!islandStarted) {
                initIsland();
            }
            processPortalDeaths();
            if (ctx.pathing.distanceTo(currentPortal.walkTile) <= 10) {
                if (!ctx.npcs.populate().filter(currentPortal.npcId).isEmpty()) {
                    SimpleNpc portal = ctx.npcs.nearest().next();
                    if (portal != null) {
                        status = "Attacking portal";
                        portal.interact("Attack");
                        ctx.onCondition(() -> ctx.players.getLocal().getInteracting() != null, 250, 10);
                    }
                }
            } else {
                status = "Walking to portal";
                ctx.pathing.step(currentPortal.walkTile);
            }
        } else {
            ctx.log("We aren't inside of pest control");
        }
    }

    private void initIsland() {
        islandStarted = true;
        deadPortals = new boolean[4];
        currentPortal = Portal.values()[between(0, 3)];
    }

    private void processPortalDeaths() {
        for (int i = 0; i < 4; i ++) {
            Portal portal = Portal.values()[i];
            if (ctx.pathing.distanceTo(portal.walkTile) <= 14) {
                if (ctx.npcs.populate().filter(portal.npcId).isEmpty()) {
                    deadPortals[i] = true;
                } else if (ctx.npcs.nearest().next().isDead()) {
                    deadPortals[i] = true;
                }
            }
        }
        if (deadPortals[currentPortal.ordinal()]) {
            int closest = -1;
            for (int i = 0; i < 4; i ++) {
                if (!deadPortals[i]) {
                    if (closest == -1 || ctx.pathing.distanceTo(Portal.values()[i].walkTile) < ctx.pathing.distanceTo(Portal.values()[closest].walkTile)) {
                        closest = i;
                    }
                }
            }
            currentPortal = Portal.values()[closest];
        }
    }

    private int between(final int min, final int max) {
        try {
            return min + (max == min ? 0 : random.nextInt(max - min));
        } catch (Exception e) {
            return min + (max - min);
        }
    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onPaint(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(5, 2, 192, 72);
        g.setColor(Color.decode("#c8d547"));
        g.drawRect(5, 2, 192, 72);
        g.drawLine(8, 24, 194, 24);

        g.setColor(Color.decode("#e0ad01"));
        g.drawString("RPest Control                            v. " + "0.1", 12, 20);
        g.drawString("Time: " + ctx.paint.formatTime(System.currentTimeMillis() - startTime), 14, 42);
        g.drawString("Status: " + status, 14, 56);

        g.drawString("Points: " + gainedPoints + " (" + ctx.paint.formatValue(ctx.paint.valuePerHour(gainedPoints, startTime)) + ")", 14, 70);
    }

    @Override
    public void onChatMessage(ChatMessageEvent event) {
        if (event.getMessageType() == 0 && event.getSender().equals("")) {
            if (event.getMessage().contains("You won")) {
                Matcher matcher = Pattern.compile("\\d+").matcher(event.getMessage());
                if (matcher.find()) {
                    try {
                        gainedPoints += Integer.parseInt(matcher.group(0));
                    } catch (Exception e) {
                        gainedPoints += 10; // In case our matcher fails we just increment points by 10
                    }
                }
            }
        }
    }

    public enum Portal {

        BLUE(1742, new WorldPoint(2646, 2572, 0)),
        YELLOW(1741, new WorldPoint(2670, 2573, 0)),
        PINK(1740, new WorldPoint(2679, 2589, 0)),
        PURPLE(1739, new WorldPoint(2631, 2592, 0)),
        ;

        public final int npcId;
        public final WorldPoint walkTile;

        Portal(final int npcId, final WorldPoint walkTile) {
            this.npcId = npcId;
            this.walkTile = walkTile;
        }

    }

}

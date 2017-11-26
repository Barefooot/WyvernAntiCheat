
package mod.sin.wyvern;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Constants;
import com.wurmonline.server.Features;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.NoPathException;
import com.wurmonline.server.creatures.ai.Path;
import com.wurmonline.server.creatures.ai.PathFinder;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.zones.Water;
import com.wurmonline.server.zones.Zones;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.mods.wyvernmods.WyvernMods;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.logging.Logger;

public class AntiCheat {
    private static Logger logger;
    private static final int emptyRock;
    
    static {
        AntiCheat.logger = Logger.getLogger(AntiCheat.class.getName());
        emptyRock = Tiles.encode((short)(-100), Tiles.Tile.TILE_CAVE_WALL.id, (byte)0);
    }
    
    private static boolean isCaveWall(final int xx, final int yy) {
        return xx < 0 || xx >= Zones.worldTileSizeX || yy < 0 || yy >= Zones.worldTileSizeY || Tiles.isSolidCave(Tiles.decodeType(Server.caveMesh.data[xx | yy << Constants.meshSize]));
    }
    
    private static boolean isSurroundedByCaveWalls(final int tilex, final int tiley) {
        int xx = tilex + 1;
        if (!isCaveWall(xx, tiley)) {
            return false;
        }
        xx = tilex - 1;
        if (!isCaveWall(xx, tiley)) {
            return false;
        }
        int yy = tiley + 1;
        if (!isCaveWall(tilex, yy)) {
            return false;
        }
        yy = tiley - 1;
        return isCaveWall(tilex, yy);
    }
    
    private static int getDummyWallAntiCheat(final int tilex, final int tiley) {
        return Tiles.encode(Tiles.decodeHeight(Server.caveMesh.data[tilex | tiley << Constants.meshSize]), Tiles.Tile.TILE_CAVE_WALL.id, Tiles.decodeData(Server.caveMesh.data[tilex | tiley << Constants.meshSize]));
    }
    
    public static void sendCaveStripAntiCheat(final Communicator comm, final short xStart, final short yStart, final int width, final int height) {
        if (comm.player != null && comm.player.hasLink()) {
            try {
                final ByteBuffer bb = comm.getConnection().getBuffer();
                bb.put((byte)102);
                bb.put((byte)(Features.Feature.CAVEWATER.isEnabled() ? 1 : 0));
                bb.put((byte)(comm.player.isSendExtraBytes() ? 1 : 0));
                bb.putShort(xStart);
                bb.putShort(yStart);
                bb.putShort((short)width);
                bb.putShort((short)height);
                final boolean onSurface = comm.player.isOnSurface();
                for (int x = 0; x < width; ++x) {
                    for (int y = 0; y < height; ++y) {
                        int xx = xStart + x;
                        int yy = yStart + y;
                        if (xx < 0 || xx >= Zones.worldTileSizeX || yy < 0 || yy >= Zones.worldTileSizeY) {
                            bb.putInt(AntiCheat.emptyRock);
                            xx = 0;
                            yy = 0;
                        }
                        else if (!onSurface) {
                            if (Tiles.decodeType(Server.caveMesh.getTile(xx, yy)) != Tiles.Tile.TILE_CAVE_EXIT.id && isSurroundedByCaveWalls(xx, yy)) {
                                bb.putInt(getDummyWallAntiCheat(xx, yy));
                            }
                            else {
                                bb.putInt(Server.caveMesh.data[xx | yy << Constants.meshSize]);
                            }
                        }
                        else if (Tiles.isSolidCave(Tiles.decodeType(Server.caveMesh.data[xx | yy << Constants.meshSize]))) {
                            bb.putInt(getDummyWallAntiCheat(xx, yy));
                        }
                        else {
                            bb.putInt(Server.caveMesh.data[xx | yy << Constants.meshSize]);
                        }
                        if (Features.Feature.CAVEWATER.isEnabled()) {
                            bb.putShort((short)Water.getCaveWater(xx, yy));
                        }
                        if (comm.player.isSendExtraBytes()) {
                            bb.put(Server.getClientCaveFlags(xx, yy));
                        }
                    }
                }
                comm.getConnection().flush();
            }
            catch (Exception ex) {
                comm.player.setLink(false);
            }
        }
    }
    
    public static boolean isVisibleThroughTerrain(final Creature performer, final Creature defender) {
        int trees = 0;
        final PathFinder pf = new PathFinder(true);
        try {
            final Path path = pf.rayCast(performer.getCurrentTile().tilex, performer.getCurrentTile().tiley, defender.getCurrentTile().tilex, defender.getCurrentTile().tiley, performer.isOnSurface(), ((int)Creature.getRange(performer, (double)defender.getPosX(), (double)defender.getPosY()) >> 2) + 5);
            final float initialHeight = Math.max(-1.4f, performer.getPositionZ() + performer.getAltOffZ() + 1.4f);
            final float targetHeight = Math.max(-1.4f, defender.getPositionZ() + defender.getAltOffZ() + 1.4f);
            double distx = Math.pow(performer.getCurrentTile().tilex - defender.getCurrentTile().tilex, 2.0);
            double disty = Math.pow(performer.getCurrentTile().tiley - defender.getCurrentTile().tiley, 2.0);
            final double dist = Math.sqrt(distx + disty);
            final double dx = (targetHeight - initialHeight) / dist;
            while (!path.isEmpty()) {
                final PathTile p = path.getFirst();
                if (Tiles.getTile(Tiles.decodeType(p.getTile())).isTree()) {
                    ++trees;
                }
                distx = Math.pow(p.getTileX() - defender.getCurrentTile().tilex, 2.0);
                disty = Math.pow(p.getTileY() - defender.getCurrentTile().tiley, 2.0);
                final double currdist = Math.sqrt(distx + disty);
                final float currHeight = Math.max(-1.4f, Zones.getLowestCorner(p.getTileX(), p.getTileY(), performer.getLayer()));
                final double distmod = currdist * dx;
                if (dx < 0.0) {
                    if (currHeight > targetHeight - distmod) {
                        return false;
                    }
                }
                else if (currHeight > targetHeight - distmod) {
                    return false;
                }
                path.removeFirst();
            }
            if (trees >= 5) {
                return false;
            }
        }
        catch (NoPathException np) {
            performer.getCommunicator().sendCombatNormalMessage("You fail to get a clear shot.");
            return false;
        }
        return true;
    }
    
    public static boolean isVisibleToAntiCheat(final Creature cret, final Creature watcher) {
        if (!cret.isVisible()) {
            return cret.getPower() > 0 && cret.getPower() <= watcher.getPower();
        }
        if (!cret.isStealth()) {
            return !WyvernMods.espCounter || (!cret.isPlayer() && cret.getLeader() == null && !cret.isRidden()) || cret.isWithinDistanceTo(watcher, 100.0f) || isVisibleThroughTerrain(cret, watcher);
        }
        if (cret.getPower() > 0 && cret.getPower() <= watcher.getPower()) {
            return true;
        }
        if (cret.getPower() < watcher.getPower()) {
            return true;
        }
        if (watcher.isUnique() || watcher.isDetectInvis()) {
            return true;
        }
        Set<Long> stealthBreakers = null;
        try {
            stealthBreakers = (Set<Long>)ReflectionUtil.getPrivateField((Object)cret, ReflectionUtil.getField((Class)cret.getClass(), "stealthBreakers"));
        }
        catch (IllegalArgumentException | IllegalAccessException | ClassCastException | NoSuchFieldException ex2) {
            final Exception ex;
            final Exception e = ex2;
            AntiCheat.logger.info("Failed to get stealthBreakers for creature " + cret.getName());
            e.printStackTrace();
        }
        return stealthBreakers != null && stealthBreakers.contains(watcher.getWurmId());
    }
    
    public static void preInit() {
        try {
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctCommunicator = classPool.get("com.wurmonline.server.creatures.Communicator");
            ctCommunicator.getDeclaredMethod("sendCaveStrip").setBody("{  mod.sin.wyvern.AntiCheat.sendCaveStripAntiCheat(this, $$);}");
            final CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            ctCreature.getDeclaredMethod("isVisibleTo").setBody("{  return mod.sin.wyvern.AntiCheat.isVisibleToAntiCheat(this, $$);}");
            final CtClass ctVirtualZone = classPool.get("com.wurmonline.server.zones.VirtualZone");
            ctVirtualZone.getDeclaredMethod("coversCreature").insertBefore("if(!this.covers($1.getTileX(), $1.getTileY())){  return false;}if(!mod.sin.wyvern.AntiCheat.isVisibleToAntiCheat($1, this.watcher)){  return false;}");
        }
        catch (CannotCompileException | NotFoundException | IllegalArgumentException | ClassCastException ex2) {
            final Exception ex;
            final Exception e = ex2;
            throw new HookException((Throwable)e);
        }
    }
}

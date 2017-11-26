// 
// Decompiled by Procyon v0.5.30
// 

package org.gotti.wurmunlimited.mods.wyvernmods;

import com.wurmonline.server.players.Player;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import mod.sin.wyvern.AntiCheat;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class WyvernMods implements WurmServerMod, Configurable, PreInitable {
    private static Logger logger;
    public static boolean espCounter;
    boolean bDebug;

    static {
        WyvernMods.logger = Logger.getLogger(WyvernMods.class.getName());
        WyvernMods.espCounter = false;
    }

    public WyvernMods() {
        this.bDebug = false;
    }

    public static boolean customCommandHandler(final ByteBuffer byteBuffer, final Player player) throws UnsupportedEncodingException {
        byte[] tempStringArr = new byte[byteBuffer.get() & 0xFF];
        byteBuffer.get(tempStringArr);
        final String message = new String(tempStringArr, "UTF-8");
        tempStringArr = new byte[byteBuffer.get() & 0xFF];
        byteBuffer.get(tempStringArr);
        if (player.mayMute() && message.startsWith("!")) {
            WyvernMods.logger.info("Player " + player.getName() + " used custom WyvernMods command: " + message);
            if (message.startsWith("!toggleESP") && player.getPower() >= 5) {
                WyvernMods.espCounter = !WyvernMods.espCounter;
                player.getCommunicator().sendSafeServerMessage("ESP counter for this server is now = " + WyvernMods.espCounter);
            } else {
                player.getCommunicator().sendSafeServerMessage("Custom command not found: " + message);
            }
            return true;
        }
        return false;
    }

    public void configure(final Properties properties) {
        this.bDebug = Boolean.parseBoolean(properties.getProperty("debug", Boolean.toString(this.bDebug)));
        try {
            final String logsPath = Paths.get("mods", new String[0]) + "/logs/";
            final File newDirectory = new File(logsPath);
            if (!newDirectory.exists()) {
                newDirectory.mkdirs();
            }
            final FileHandler fh = new FileHandler(String.valueOf(String.valueOf(String.valueOf(logsPath))) + this.getClass().getSimpleName() + ".log", 10240000, 200, true);
            if (this.bDebug) {
                fh.setLevel(Level.INFO);
            } else {
                fh.setLevel(Level.WARNING);
            }
            fh.setFormatter(new SimpleFormatter());
            WyvernMods.logger.addHandler(fh);
        } catch (IOException ie) {
            System.err.println(String.valueOf(String.valueOf(this.getClass().getName())) + ": Unable to add file handler to logger");
        }
        this.Debug("Debugging messages are enabled.");
    }

    private void Debug(final String x) {
        if (this.bDebug) {
            System.out.println(String.valueOf(String.valueOf(this.getClass().getSimpleName())) + ": " + x);
            System.out.flush();
            WyvernMods.logger.log(Level.INFO, x);
        }
    }

    public void preInit() {
        WyvernMods.logger.info("Pre-Initializing.");
        try {
            AntiCheat.preInit();
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctCommunicator = classPool.get("com.wurmonline.server.creatures.Communicator");
            ctCommunicator.getDeclaredMethod("reallyHandle").instrument((ExprEditor) new ExprEditor() {
                public void edit(final MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("reallyHandle_CMD_MESSAGE")) {
                        m.replace("java.nio.ByteBuffer tempBuffer = $1.duplicate();if(!org.gotti.wurmunlimited.mods.wyvernmods.WyvernMods.customCommandHandler($1, this.player)){  $_ = $proceed(tempBuffer);}");
                    }
                }
            });
        } catch (CannotCompileException | NotFoundException | IllegalArgumentException | ClassCastException ex2) {
            final Exception ex;
            final Exception e = ex2;
            throw new HookException((Throwable) e);
        }
    }
}
    

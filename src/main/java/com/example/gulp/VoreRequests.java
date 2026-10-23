package com.example.gulp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in swallow requests between players. Swallowing a player never happens straight away: the target gets
 * clickable [Accept] / [Decline] buttons in chat (they run /gulp accept and /gulp decline) and is only swallowed
 * if they accept in time. Everything is checked again at the moment they accept.
 */
public final class VoreRequests {
    /** How long the target has to answer (30 seconds). */
    public static final int EXPIRE_TICKS = 600;
    /** After a decline, the same predator can't ask the same player again for this long (60 seconds). */
    public static final int DECLINE_BLOCK_TICKS = 1200;
    /** The predator must still be this close (blocks) when the target accepts. */
    public static final double MAX_DISTANCE = 8.0;

    private record Request(UUID pred, int expires, boolean hard) { }

    /** target -> the request waiting for their answer (one at a time). Only lives while the server runs. */
    private static final Map<UUID, Request> PENDING = new HashMap<>();
    /** "pred>target" -> tick until which that predator may not ask that target again. */
    private static final Map<String, Integer> DECLINED = new HashMap<>();

    private VoreRequests() { }

    private static String key(UUID pred, UUID target) { return pred + ">" + target; }

    /** The predator asks. Role, capacity and everything else was already checked by PlayerHolding.request. */
    public static void send(ServerPlayer pred, ServerPlayer victim) {
        int now = pred.getServer().getTickCount();
        PENDING.values().removeIf(r -> r.expires() <= now);
        DECLINED.values().removeIf(t -> t <= now);

        if (DECLINED.containsKey(key(pred.getUUID(), victim.getUUID()))) {
            StomachLogic.say(pred, Component.empty().append(victim.getName())
                    .append(" declined your request a moment ago. Give them a minute."));
            return;
        }
        Request existing = PENDING.get(victim.getUUID());
        if (existing != null) {
            StomachLogic.say(pred, Component.empty().append(victim.getName()).append(
                    existing.pred().equals(pred.getUUID()) ? " hasn't answered your request yet." : " already has a request waiting."));
            return;
        }
        boolean hard = PlayerHolding.hardVore(StomachManager.get(pred.getServer()).of(pred.getUUID()));
        PENDING.put(victim.getUUID(), new Request(pred.getUUID(), now + EXPIRE_TICKS, hard));

        MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Let them swallow you"))));
        MutableComponent decline = Component.literal("[Decline]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gulp decline"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Say no"))));
        // Say clearly what accepting means, so nobody agrees to something they didn't expect.
        String terms;
        if (hard) {
            int minutes = Math.max(1, Math.round(GulpServerConfig.PLAYER_DIGEST_SECONDS.get() / 60f));
            terms = "HARD mode: you will be digested over about " + minutes + " min and can't just leave. You can struggle (R) to try to break free. "
                    + (GulpServerConfig.PLAYER_DIGESTION_KILLS.get()
                    ? "If the timer runs out you will DIE. " : "If the timer runs out you are let out, weakened. ");
        } else {
            terms = "Soft mode: you're only held, and can leave any time with Release (G). ";
        }
        victim.sendSystemMessage(Component.empty().append(pred.getName())
                .append(" wants to swallow you. ")
                .append(Component.literal(terms).withStyle(hard ? ChatFormatting.RED : ChatFormatting.YELLOW))
                .append(accept).append(" ").append(decline)
                .append(Component.literal(" (30 seconds, or type /gulp accept or /gulp decline)").withStyle(ChatFormatting.GRAY)));
        victim.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.5f);
        StomachLogic.say(pred, Component.literal("Asked ").append(victim.getName())
                .append(". They have 30 seconds to answer."));
    }

    /** The target answers (clicked a button or typed the command). */
    public static void respond(ServerPlayer victim, boolean accept) {
        MinecraftServer server = victim.getServer();
        int now = server.getTickCount();
        Request r = PENDING.remove(victim.getUUID());
        if (r == null || r.expires() <= now) {
            victim.sendSystemMessage(Component.literal("You have no swallow request waiting."));
            return;
        }
        ServerPlayer pred = server.getPlayerList().getPlayer(r.pred());
        if (pred == null || !pred.isAlive()) {
            victim.sendSystemMessage(Component.literal("They're no longer around."));
            return;
        }
        if (!accept) {
            DECLINED.put(key(pred.getUUID(), victim.getUUID()), now + DECLINE_BLOCK_TICKS);
            victim.sendSystemMessage(Component.literal("You declined."));
            StomachLogic.say(pred, Component.empty().append(victim.getName()).append(" declined your request."));
            return;
        }
        if (pred.level() != victim.level() || pred.distanceToSqr(victim) > MAX_DISTANCE * MAX_DISTANCE) {
            victim.sendSystemMessage(Component.literal("They're too far away now."));
            StomachLogic.say(pred, Component.empty().append(victim.getName())
                    .append(" accepted, but you're too far away. Ask again up close."));
            return;
        }
        StomachManager mgr = StomachManager.get(server);
        Stomach ps = mgr.of(pred.getUUID());
        // They agreed to a specific mode. If the swallower switched since, the answer no longer counts.
        if (PlayerHolding.hardVore(ps) != r.hard()) {
            victim.sendSystemMessage(Component.literal("The swallower changed mode since they asked, so nothing happened. Ask again."));
            StomachLogic.say(pred, Component.empty().append(victim.getName())
                    .append(" accepted, but you changed mode. Ask again."));
            return;
        }
        // Checked again here: roles, capacity and everything else may have changed since the request was sent.
        if (PlayerHolding.swallow(pred, ps, victim, mgr)) {
            StomachLogic.finishSwallow(pred, ps, victim.getName());
            mgr.setDirty();
            StomachLogic.sync(pred, ps);
        } else {
            victim.sendSystemMessage(Component.literal("You couldn't be swallowed right now."));
        }
    }
}

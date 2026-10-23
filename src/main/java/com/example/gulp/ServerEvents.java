package com.example.gulp;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.PotionItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = Gulp.ID)
public final class ServerEvents {
    private ServerEvents() { }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer p)) return;
        PlayerHolding.tickHeld(p); // keeps a swallowed player inside
        StomachManager mgr = StomachManager.get(p.getServer());
        StomachLogic.tickPlayer(mgr, p, mgr.of(p.getUUID()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerHolding.onLogin(p);
        StomachLogic.sync(p, StomachManager.get(p.getServer()).of(p.getUUID()));
        RolesPacket.broadcast(p.getServer()); // everyone learns the new player's role, and the new player learns everyone's
        // Test/dev helper: tell the player whether we connected to Minecraft Comes Alive.
        if (GulpServerConfig.MCA_STATUS_MESSAGE.get()) p.sendSystemMessage(McaIntegration.status());
    }

    // Potions the swallower drinks also affect the players inside their stomach.
    @SubscribeEvent
    public static void onPotionDrunk(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getItem().getItem() instanceof PotionItem)) return;
        PlayerHolding.transferPotion(p, e.getItem());
    }

    // The night was skipped: players inside a sleeping swallower's stomach sleep through it too.
    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSleeping()) PlayerHolding.sleepThroughNight(p);
        }
    }

    // /gulp accept and /gulp decline: the answer to a swallow request (the chat buttons run these).
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("gulp")
                .then(Commands.literal("accept").executes(c -> {
                    VoreRequests.respond(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("decline").executes(c -> {
                    VoreRequests.respond(c.getSource().getPlayerOrException(), false);
                    return 1;
                })));
    }

    // Anyone swallowed (or swallowing) when a player leaves is let out, so nobody is left stuck as a spectator.
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerHolding.onLogout(p);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerHolding.releaseAllHeldBy(p);
    }

    // ---- MCA: frightened villagers refuse to talk, trade or marry you (all of those start with a right-click)

    @SubscribeEvent
    public static void onVillagerClick(PlayerInteractEvent.EntityInteract e) {
        refuseIfAfraid(e, e.getTarget());
    }

    @SubscribeEvent
    public static void onVillagerClickAt(PlayerInteractEvent.EntityInteractSpecific e) {
        refuseIfAfraid(e, e.getTarget());
    }

    private static void refuseIfAfraid(PlayerInteractEvent e, Entity target) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Component refusal = McaIntegration.refusal(p, target);
        if (refusal == null) return;
        if (McaIntegration.shouldTell(p)) StomachLogic.say(p, refusal);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // Keeps villagers who just saw something frightening running for a few seconds.
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getTickCount() % 10 == 0) McaIntegration.tickScared(server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        PlayerHolding.onServerStopping(e.getServer());
    }

    // Don't lose swallowed mobs if the player dies.
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) StomachLogic.releaseAll(p);
    }

    // A mob being digested: its loot goes straight into the player's inventory instead of onto the ground...
    @SubscribeEvent
    public static void onDigestDrops(LivingDropsEvent e) {
        if (StomachLogic.digestingPlayer() == null || e.getEntity() != StomachLogic.digestingMob()) return;
        StomachLogic.giveDrops(e.getDrops());
        e.setCanceled(true);
    }

    // ...and so does its XP, with no orbs popping out.
    @SubscribeEvent
    public static void onDigestXp(LivingExperienceDropEvent e) {
        ServerPlayer player = StomachLogic.digestingPlayer();
        if (player == null || e.getEntity() != StomachLogic.digestingMob()) return;
        if (e.getDroppedExperience() > 0) player.giveExperiencePoints(e.getDroppedExperience());
        e.setCanceled(true);
    }

    // Rich Digestion perk: extra Looting levels, but only for mobs being digested right now.
    @SubscribeEvent
    public static void onLooting(LootingLevelEvent e) {
        int bonus = StomachLogic.pendingLooting();
        if (bonus > 0 && e.getDamageSource() != null && e.getDamageSource().getEntity() instanceof ServerPlayer) {
            e.setLootingLevel(e.getLootingLevel() + bonus);
        }
    }
}

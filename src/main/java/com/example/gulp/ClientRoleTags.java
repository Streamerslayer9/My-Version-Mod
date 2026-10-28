package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Puts each player's role badge in the name tag above their head, in the role's colour: "Streamerslayer9 [Prey]".
 * Client only: chat and everyone else's view of names are untouched. Turn it off with "Role in name tags" in Settings.
 */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class ClientRoleTags {
    private ClientRoleTags() { }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide || !GulpConfig.showRoleTags) return; // the integrated server's copy of you is left alone
        Role role = ClientState.roleOf(p.getUUID());
        if (role == null) return; // the server hasn't told us this player's role (yet)
        e.setDisplayname(Component.empty().append(e.getDisplayname()).append(" ")
                .append(Component.literal("[" + role.label + "]").withStyle(st -> st.withColor(role.color & 0xFFFFFF))));
    }

    /** Name tags are cached by Minecraft, so rebuild them once a second: whatever order things arrived in, the badge appears. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getGameTime() % 20 == 0) refreshAll();
    }

    /** /gulproles (client side): lists every player this client can see and the role it knows for them. For bug reports. */
    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("gulproles").executes(c -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return 0;
            for (Player p : level.players()) {
                Role role = ClientState.roleOf(p.getUUID());
                c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + "  uuid " + p.getUUID() + "  role "
                        + (role == null ? "UNKNOWN (the server hasn't sent it)" : role.label)
                        + "  tags " + (GulpConfig.showRoleTags ? "on" : "off")), false);
            }
            return 1;
        }));
    }

    /** Names are cached, so ask every player nearby to rebuild theirs (a role changed, or the setting was switched). */
    public static void refreshAll() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Player p : level.players()) p.refreshDisplayName();
    }
}

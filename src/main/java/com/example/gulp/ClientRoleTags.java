package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
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

    /** Names are cached, so ask every player nearby to rebuild theirs (a role changed, or the setting was switched). */
    public static void refreshAll() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Player p : level.players()) p.refreshDisplayName();
    }
}

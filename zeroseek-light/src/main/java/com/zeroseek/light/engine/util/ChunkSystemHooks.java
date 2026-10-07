package com.zeroseek.light.engine.util;

import com.zeroseek.light.engine.StarLightInterface;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class ChunkSystemHooks {

    public static boolean isTicketThreadSafe() {
        return false;
    }

    public static boolean isNonFullTicket() {
        return false;
    }

    public static boolean avoidLightCopy() {
        return false;
    }

    public static void addLightTicket(ServerLevel world, ChunkPos pos) {
        world.getChunkSource().addTicketWithRadius(StarLightInterface.CHUNK_WORK_TICKET, pos, 0);
    }

    public static void removeLightTicket(ServerLevel world, ChunkPos pos) {
        world.getChunkSource().removeTicketWithRadius(StarLightInterface.CHUNK_WORK_TICKET, pos, 0);
    }

}

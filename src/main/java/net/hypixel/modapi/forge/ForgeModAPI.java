package net.hypixel.modapi.forge;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

@Mod(modid = ForgeModAPI.MODID, version = ForgeModAPI.VERSION, clientSideOnly = true, name = "Hypixel Mod API")
public class ForgeModAPI {
    public static final String MODID = "hypixel_mod_api";
    public static final String VERSION = "0.5.0";
    private static final Logger LOGGER = Logger.getLogger("HypixelModAPI");

    // We store a local reference to the net handler, so it's instantly available from the moment we connect
    private NetHandlerPlayClient netHandler;

    @Mod.EventHandler
    public void init(FMLPostInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
        HypixelModAPI.getInstance().setPacketSender(this::sendPacket);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        netHandler = (NetHandlerPlayClient) event.handler;
        event.manager.channel().pipeline().addBefore("packet_handler", "hypixel_mod_api_packet_handler", HypixelPacketHandler.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        netHandler = null;
    }

    private boolean sendPacket(HypixelPacket packet) {
        if (netHandler == null) {
            return false;
        }

        if (!netHandler.getNetworkManager().isChannelOpen()) {
            LOGGER.warning("Attempted to send packet while channel is closed!");
            netHandler = null;
            return false;
        }

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        PacketSerializer serializer = new PacketSerializer(buf);
        packet.write(serializer);
        netHandler.addToSendQueue(new C17PacketCustomPayload(packet.getIdentifier(), buf));
        return true;
    }

    @ChannelHandler.Sharable
    private static class HypixelPacketHandler extends SimpleChannelInboundHandler<Packet<?>> {
        private static final HypixelPacketHandler INSTANCE = new HypixelPacketHandler();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<?> msg) {
            ctx.fireChannelRead(msg);

            if (!(msg instanceof S3FPacketCustomPayload)) {
                return;
            }

            S3FPacketCustomPayload packet = (S3FPacketCustomPayload) msg;
            String identifier = packet.getChannelName();
            if (!HypixelModAPI.getInstance().getRegistry().isRegistered(identifier)) {
                return;
            }

            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    HypixelModAPI.getInstance().handle(identifier, new PacketSerializer(packet.getBufferData()));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to handle packet " + identifier, e);
                }
            });
        }
    }
}

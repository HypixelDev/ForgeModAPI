package net.hypixel.modapi.forge;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.HypixelModAPIImplementation;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = ForgeModAPI.MODID, version = ForgeModAPI.VERSION, clientSideOnly = true, name = "Hypixel Mod API")
public class ForgeModAPI implements HypixelModAPIImplementation {
    public static final String MODID = "hypixel_mod_api";
    public static final String VERSION = "${version}";
    private static final Logger LOGGER = LogManager.getLogger("HypixelModAPI");
    private static final boolean DEBUG_MODE = Boolean.getBoolean("net.hypixel.modapi.debug");

    // We store a local reference to the net handler, so it's instantly available from the moment we connect
    private NetHandlerPlayClient netHandler;
    private boolean onHypixel;

    @Mod.EventHandler
    public void init(FMLPostInitializationEvent event) {
        HypixelModAPI.getInstance().setModImplementation(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        netHandler = (NetHandlerPlayClient) event.handler;
        event.manager.channel().pipeline().addBefore("packet_handler", "hypixel_mod_api_packet_handler", HypixelPacketHandler.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        netHandler = null;
        onHypixel = false;
    }

    @Override
    public void onInit() {
        HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, packet -> onHypixel = true);
        MinecraftForge.EVENT_BUS.register(this);

        if (DEBUG_MODE) {
            LOGGER.info("Debug mode is enabled!");
            registerDebug();
        }
    }

    @Override
    public boolean sendPacket(HypixelPacket packet) {
        if (netHandler == null) {
            return false;
        }

        if (!isConnectedToHypixel()) {
            return false;
        }

        if (!netHandler.getNetworkManager().isChannelOpen()) {
            LOGGER.warn("Attempted to send packet while channel is closed!");
            netHandler = null;
            return false;
        }

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        PacketSerializer serializer = new PacketSerializer(buf);
        packet.write(serializer);
        netHandler.addToSendQueue(new C17PacketCustomPayload(packet.getIdentifier(), buf));
        return true;
    }

    @Override
    public boolean isConnectedToHypixel() {
        return onHypixel;
    }

    @ChannelHandler.Sharable
    private static class HypixelPacketHandler extends SimpleChannelInboundHandler<Packet<?>> {
        private static final HypixelPacketHandler INSTANCE = new HypixelPacketHandler();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<?> msg) {
            if (!(msg instanceof S3FPacketCustomPayload)) {
                ctx.fireChannelRead(msg);
                return;
            }

            S3FPacketCustomPayload packet = (S3FPacketCustomPayload) msg;
            String identifier = packet.getChannelName();
            if (!HypixelModAPI.getInstance().getRegistry().isRegistered(identifier)) {
                ctx.fireChannelRead(msg);
                return;
            }

            PacketBuffer buffer = packet.getBufferData();
            buffer.retain();
            ctx.fireChannelRead(msg);
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    HypixelModAPI.getInstance().handle(identifier, new PacketSerializer(buffer));
                } catch (Exception e) {
                    LOGGER.warn("Failed to handle packet {}", identifier, e);
                } finally {
                    buffer.release();
                }
            });
        }
    }

    private static void registerDebug() {
        // Register events
        HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);

        HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> LOGGER.info("Received location packet {}", packet))
                .onError(error -> LOGGER.error("Received error response for location packet: {}", error));
    }
}

package xyz.breadloaf.chatlinks.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatMixin {
    private static final Pattern URL_PATTERN = Pattern.compile("((?:https?:\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Shadow public ServerPlayer player;

    @Shadow public abstract void send(Packet<?> packet);

    @Shadow protected abstract void handleCommand(String string);

    @Shadow @Final private MinecraftServer server;

    @Shadow private int chatSpamTickCount;

    @Shadow public abstract void disconnect(Component component);

    @Inject(at = @At("HEAD"), method="handleChat(Lnet/minecraft/server/network/TextFilter$FilteredText;)V", cancellable = true)
    public void injected(TextFilter.FilteredText filteredText, CallbackInfo ci){
        if (player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            send(new ClientboundChatPacket((new TranslatableComponent("chat.disabled.options")).withStyle(ChatFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID));
        } else {
            player.resetLastActionTime();
            String string = filteredText.getRaw();
            if (string.startsWith("/")) {
                handleCommand(string);
            } else {
                String string2 = filteredText.getFiltered();
                Object[] msg = new Object[]{this.player.getDisplayName(), replaceLinks(string2)};
                Component component = string2.isEmpty() ? null : new TranslatableComponent("chat.type.text", msg);
                Component component2 = new TranslatableComponent("chat.type.text", msg);
                server.getPlayerList().broadcastMessage(component2, (serverPlayer) -> player.shouldFilterMessageTo(serverPlayer) ? component : component2, ChatType.CHAT, player.getUUID());
            }

            chatSpamTickCount += 20;
            if (chatSpamTickCount > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) {
                disconnect(new TranslatableComponent("disconnect.spam"));
            }

        }
        ci.cancel();
    }

    public MutableComponent replaceLinks(String msg) {
        TextComponent val = new TextComponent("");
        Matcher matcher = URL_PATTERN.matcher(msg);
        int start;
        int end;
        int lastMatch = 0;
        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
            val.append(msg.substring(lastMatch,start));
            lastMatch = end;
            String url = msg.substring(start,end);
            val.append(new TextComponent(url).withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,url))).withStyle(ChatFormatting.UNDERLINE));
        }
        val.append(msg.substring(lastMatch));
        return  val;
    }
}

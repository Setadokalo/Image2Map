package space.essem.image2map;

import net.fabricmc.api.ModInitializer;
import space.essem.image2map.config.Image2MapConfig;
import space.essem.image2map.renderer.MapRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import org.jetbrains.annotations.Nullable;

import me.sargunvohra.mcmods.autoconfig1u.AutoConfig;
import me.sargunvohra.mcmods.autoconfig1u.serializer.GsonConfigSerializer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.item.ItemStack;
import net.minecraft.command.argument.NumberRangeArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public class Image2Map implements ModInitializer {
    public static Image2MapConfig CONFIG = AutoConfig.register(Image2MapConfig.class, GsonConfigSerializer::new)
            .getConfig();

    @Override
    public void onInitialize() {
        System.out.println("Loading Image2Map...");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(CommandManager.literal("mapcreate")
                    .requires(source -> source.hasPermissionLevel(CONFIG.minPermLevel))
                    .then(CommandManager.argument("mode", StringArgumentType.word()).suggests(new DitherModeSuggestionProvider())
                    .then(CommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(this::createMap))));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(CommandManager.literal("multimapcreate")
                    .requires(source -> source.hasPermissionLevel(CONFIG.minPermLevel))
                    .then(CommandManager.argument("mode", StringArgumentType.word()).suggests(new DitherModeSuggestionProvider())
                    .then(CommandManager.argument("sizex", IntegerArgumentType.integer(1, 10))
                    .then(CommandManager.argument("sizey", IntegerArgumentType.integer(1, 10))
                    .then(CommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(this::createMaps))))));
        });
    }

    class DitherModeSuggestionProvider implements SuggestionProvider<ServerCommandSource> {

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context,
                SuggestionsBuilder builder) throws CommandSyntaxException {
            builder.suggest("none");
            builder.suggest("dither");
            builder.suggest("ordered");
            return builder.buildFuture();
        }
        
    }

    public enum DitherMode {
        NONE,
        FLOYD,
        ORDERED;

        public static DitherMode fromString(String string) {
            if (string.equalsIgnoreCase("NONE"))
                return DitherMode.NONE;
            else if (string.equalsIgnoreCase("DITHER") || string.equalsIgnoreCase("FLOYD"))
                    return DitherMode.FLOYD;
            else if (string.equalsIgnoreCase("ORDERED"))
                    return DitherMode.ORDERED;
            throw new IllegalArgumentException("invalid dither mode");
        }
    }

    private int createMap(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Vec3d pos = source.getPosition();
        PlayerEntity player = source.getPlayer();
        DitherMode mode = null;
        String modeStr = StringArgumentType.getString(context, "mode");
        try {
            mode = DitherMode.fromString(modeStr);
        } catch (IllegalArgumentException e) {
            throw new SimpleCommandExceptionType(() -> "Invalid dither mode '" + modeStr + "'").create();
        }
        String input = StringArgumentType.getString(context, "path");

        source.sendFeedback(new LiteralText("Generating image map..."), false);
        BufferedImage image = getImage(input, source);
        
        
        MapRenderer.render(image, mode, source.getWorld(), pos, 1, 1, player);
        source.sendFeedback(new LiteralText("Done!"), false);

        return 1;
    }

    private int createMaps(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Vec3d pos = source.getPosition();
        PlayerEntity player = source.getPlayer();
        DitherMode mode = null;
        String modeStr = StringArgumentType.getString(context, "mode");
        final int countX = IntegerArgumentType.getInteger(context, "sizex");
        final int countY = IntegerArgumentType.getInteger(context, "sizey");
        try {
            mode = DitherMode.fromString(modeStr);
        } catch (IllegalArgumentException e) {
            throw new SimpleCommandExceptionType(() -> "Invalid dither mode '" + modeStr + "'").create();
        }
        String input = StringArgumentType.getString(context, "path");

        source.sendFeedback(new LiteralText("Generating image map..."), false);
        BufferedImage image = getImage(input, source);
        if (image == null)
            return 0;
        try {
            MapRenderer.render(image, mode, source.getWorld(), pos, countX, countY, player);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        source.sendFeedback(new LiteralText("Done!"), false);

        return 1;
    }

    @Nullable
    private BufferedImage getImage(String urlStr, ServerCommandSource source) {
        BufferedImage image = null;
        try {
            if (isValid(urlStr)) {
                URL url = new URL(urlStr);
                URLConnection connection = url.openConnection();
                connection.setRequestProperty("User-Agent", "Image2Map mod");
                connection.connect();
                image = ImageIO.read(connection.getInputStream());
            } else if (CONFIG.allowLocalFiles) {
                File file = new File(urlStr);
                image = ImageIO.read(file);
            } else {
                image = null;
            }
        } catch (IOException e) {
            source.sendFeedback(new LiteralText("That doesn't seem to be a valid image."), false);
            return null;
        }

        if (image == null) {
            source.sendFeedback(new LiteralText("That doesn't seem to be a valid image."), false);
            return null;
        }
        return image;
    }




    private static boolean isValid(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

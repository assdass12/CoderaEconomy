package tr.balzach.coderaEconomy.util;

import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern color utility supporting hex colors and gradients
 */
public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>");

    /**
     * Translates color codes including hex colors
     * Supports both legacy (&) and hex (<#RRGGBB>) formats
     */
    @NotNull
    public static String colorize(@NotNull String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Process gradients first
        message = processGradients(message);

        // Process hex colors
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            try {
                ChatColor color = ChatColor.of("#" + hexCode);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(color.toString()));
            } catch (IllegalArgumentException e) {
                // Invalid hex code, keep original
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);
        message = buffer.toString();

        // Process legacy color codes
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    /**
     * Strips all color codes from message
     */
    @NotNull
    public static String stripColor(@NotNull String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Remove hex colors
        message = HEX_PATTERN.matcher(message).replaceAll("");

        // Remove gradient tags
        message = GRADIENT_PATTERN.matcher(message).replaceAll("$3");

        // Remove legacy colors
        message = ChatColor.stripColor(message);

        return message;
    }

    /**
     * Processes gradient text
     */
    @NotNull
    private static String processGradients(@NotNull String message) {
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);

            String gradientText = applyGradient(text, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Applies gradient effect to text
     */
    @NotNull
    private static String applyGradient(@NotNull String text, @NotNull String startHex, @NotNull String endHex) {
        if (text.isEmpty()) {
            return "";
        }

        int[] startRGB = hexToRGB(startHex);
        int[] endRGB = hexToRGB(endHex);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(c);
                continue;
            }

            float ratio = length == 1 ? 0 : (float) i / (float) (length - 1);

            int r = (int) (startRGB[0] + ratio * (endRGB[0] - startRGB[0]));
            int g = (int) (startRGB[1] + ratio * (endRGB[1] - startRGB[1]));
            int b = (int) (startRGB[2] + ratio * (endRGB[2] - startRGB[2]));

            ChatColor color = ChatColor.of(String.format("#%02X%02X%02X", r, g, b));
            result.append(color).append(c);
        }

        return result.toString();
    }

    /**
     * Converts hex color to RGB array
     */
    @NotNull
    private static int[] hexToRGB(@NotNull String hex) {
        hex = hex.replace("#", "");
        return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
}
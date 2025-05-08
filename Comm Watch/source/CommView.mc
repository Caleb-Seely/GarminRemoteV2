/**
 * CommView.mc
 * Main view class for the Communication Watch app.
 * Displays the primary interface with a camera icon and capture text.
 * Handles the layout and rendering of the main screen.
 */

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Communications;
using Toybox.System;

class CommView extends WatchUi.View {

    /**
     * Initialize the view
     */
    function initialize() {
        View.initialize();
    }

  
    /**
     * Updates the view with the current state
     * @param dc Device context
     */
    function onUpdate(dc) {
        // Clear the screen with black background
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);

        if (hasDirectMessagingSupport) {
            var centerX = dc.getWidth() / 2;
            var padding = 5;

            // Load and position the camera icon
            var icon = WatchUi.loadResource(Rez.Drawables.CameraIcon);
            var iconHeight = icon.getHeight();
            var iconWidth = icon.getWidth();

            // Calculate text dimensions
            var fontHeight = dc.getFontHeight(Graphics.FONT_MEDIUM);
            var totalHeight = iconHeight + padding + fontHeight;
            var startY = (dc.getHeight() - totalHeight) / 2;

            // Position the icon
            var iconX = centerX - (iconWidth / 2);
            var iconY = startY;

            // Position the text below the icon
            var textY = iconY + iconHeight + padding;

            // Draw the icon
            dc.drawBitmap(iconX, iconY, icon);

            // Draw the "Capture" text
            dc.drawText(
                centerX,
                textY,
                Graphics.FONT_MEDIUM,
                "Capture",
                Graphics.TEXT_JUSTIFY_CENTER
            );
        } else {
            // Display message if direct messaging is not supported
            dc.drawText(
                dc.getWidth() / 2,
                dc.getHeight() / 3,
                Graphics.FONT_MEDIUM,
                "Direct Messaging API\nNot Supported",
                Graphics.TEXT_JUSTIFY_CENTER
            );
        }
    }
}
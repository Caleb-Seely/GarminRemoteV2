/**
 * GlanceView.mc
 * Glance view class for quick access to the Communication Watch app.
 * Provides a simplified view that can be accessed from the watch face.
 */

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Communications;
using Toybox.System;

/**
 * GlanceView class for displaying the quick access interface
 */
class GlanceView extends WatchUi.GlanceView {
    /**
     * Initialize the glance view
     */
    function initialize() {
        GlanceView.initialize();
    }

    /**
     * Returns the initial view and delegate
     * @return Array containing the view and delegate
     */
    function getInitialView() {
        return [new GlanceView(), new GlanceDelegate()];
    }

    /**
     * Updates the glance view with the current state
     * @param dc Device context
     */
    function onUpdate(dc) {
        // Set black background
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        
        // Draw "Quick Capture" text in white
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var fontHeight = dc.getFontHeight(Graphics.FONT_SMALL);

        dc.drawText(
            0,  // Left aligned
            (dc.getHeight() - fontHeight) / 2,  // Vertically centered
            Graphics.FONT_SMALL,
            "Quick Capture",
            Graphics.TEXT_JUSTIFY_LEFT
        );
    }
}

/**
 * GlanceDelegate class for handling glance view interactions
 */
class GlanceDelegate extends WatchUi.GlanceViewDelegate {
    /**
     * Initialize the glance delegate
     */
    function initialize() {
        GlanceViewDelegate.initialize();
    }

    /**
     * Handle tap events on the glance view
     * Sends a capture request message
     * @return true to indicate the event was handled
     */
    function onTap() {
        var listener = new CommListener();
        Communications.transmit("Glance capture request", null, listener);
        return true;
    }
} 
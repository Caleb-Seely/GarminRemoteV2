/**
 * MessageView.mc
 * View class for displaying incoming messages.
 * Shows messages with a vibration alert and automatically returns to the main view.
 */

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Attention;
using Toybox.Timer;

class MessageView extends WatchUi.View {
    private var message;  // The message to display
    private var timer;    // Timer for auto-return to main view
    
    /**
     * Initialize the message view
     * @param msg The message to display
     */
    function initialize(msg) {
        View.initialize();
        message = msg;
        timer = new Timer.Timer();
    }
    
    /**
     * Called when the view is laid out
     * @param dc Device context
     */
    function onLayout(dc) {
        // No layout needed for this simple view
    }
    
    /**
     * Called when the view becomes visible
     * Triggers vibration and starts auto-return timer
     */
    function onShow() {
        // Vibrate to alert user of new message
        if (Attention has :vibrate) {
            var vibrateData = [new Attention.VibeProfile(100, 250)];
            Attention.vibrate(vibrateData);
        }
        
        // Set timer to return to main view after 2 seconds
        timer.start(method(:onTimer), 2000, false);
    }
    
    /**
     * Called when the view is hidden
     * Stops the auto-return timer
     */
    function onHide() {
        timer.stop();
    }
    
    /**
     * Updates the view with the current state
     * @param dc Device context
     */
    function onUpdate(dc) {
        // Clear screen with black background
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        
        // Draw message in white text
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(
            dc.getWidth() / 2,
            dc.getHeight() / 2,
            Graphics.FONT_MEDIUM,
            message,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER
        );
    }
    
    /**
     * Timer callback function
     * Returns to the main view
     */
    function onTimer() {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
} 
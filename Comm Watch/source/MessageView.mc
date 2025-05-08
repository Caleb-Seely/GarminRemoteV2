using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Attention;
using Toybox.Timer;

class MessageView extends WatchUi.View {
    private var message;
    private var timer;
    
    function initialize(msg) {
        View.initialize();
        message = msg;
        timer = new Timer.Timer();
    }
    
    function onLayout(dc) {
        // No layout needed
    }
    
    function onShow() {
        // Vibrate when message is shown
        if (Attention has :vibrate) {
            var vibrateData = [new Attention.VibeProfile(100, 500)];
            Attention.vibrate(vibrateData);
        }
        
        // Set timer to return to main view after 2 seconds
        timer.start(method(:onTimer), 2000, false);
    }
    
    function onHide() {
        timer.stop();
    }
    
    function onUpdate(dc) {
        // Clear screen
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        
        // Draw message
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(
            dc.getWidth() / 2,
            dc.getHeight() / 2,
            Graphics.FONT_MEDIUM,
            message,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER
        );
    }
    
    function onTimer() {
        // Return to main view
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
} 
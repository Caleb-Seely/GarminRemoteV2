# Design Document

## Overview

The Developer Note Popup feature will be implemented as a DialogFragment-based system that displays periodic messages from the app developer (Caleb) to users. The system follows Android best practices for user engagement while respecting user experience boundaries. The implementation leverages existing patterns from the codebase, particularly the SharedPreferences approach used in InAppReviewUtils, and integrates seamlessly with the current Material Design theme.

## Architecture

### Component Structure
```
DeveloperNotePopup (DialogFragment)
├── DeveloperNoteManager (Singleton)
│   ├── SharedPreferences management
│   ├── Timing logic
│   └── Version rotation
├── DeveloperNoteContent (Data class)
│   ├── Message variations
│   └── Content metadata
└── Integration points
    ├── DeviceActivity (primary trigger)
    └── Other activities (future expansion)
```

### Data Flow
1. **App Launch**: DeviceActivity checks timing conditions via DeveloperNoteManager
2. **Condition Met**: Manager determines if popup should show and which version
3. **Display**: DialogFragment created with appropriate content and styling
4. **User Action**: User interacts with buttons or closes dialog
5. **State Update**: Manager updates SharedPreferences with new timestamps and version index

## Components and Interfaces

### DeveloperNoteManager
**Purpose**: Central coordinator for popup timing, state management, and content selection

**Key Methods**:
```kotlin
object DeveloperNoteManager {
    fun shouldShowPopup(context: Context): Boolean
    fun getNextPopupContent(): DeveloperNoteContent?
    fun recordPopupShown(context: Context)
    fun recordPopupDismissed(context: Context)
    fun isPopupCycleComplete(context: Context): Boolean
}
```

**SharedPreferences Keys**:
- `developer_note_first_install_date`: Long (timestamp)
- `developer_note_last_popup_date`: Long (timestamp) 
- `developer_note_popup_version_index`: Int (0-3, -1 for complete)

**Timing Logic**:
- Initial delay: 5 days (432,000,000 ms)
- Repeat interval: 60 days (5,184,000,000 ms)
- Version progression: 0 → 1 → 2 → 3 → complete (-1)

### DeveloperNoteContent
**Purpose**: Data structure containing popup content variations

```kotlin
data class DeveloperNoteContent(
    val version: Int,
    val title: String,
    val message: String,
    val websiteButtonText: String = "Visit My Website",
    val emailButtonText: String = "Email"
)
```

**Content Variations**:
- Version 0: Introduction and project growth focus
- Version 1: Continued opportunity seeking focus  
- Version 2: Community and usefulness focus
- Version 3: Personal persistence and scale focus

### DeveloperNoteDialogFragment
**Purpose**: UI presentation layer with Material Design styling

**Key Features**:
- Extends DialogFragment for lifecycle safety
- Custom layout with rounded corners and shadow
- Circular avatar using Glide image loading
- Material Design button styling
- Fade animations for show/hide transitions

**Layout Structure**:
```xml
<CardView> (rounded corners, elevation)
├── <ImageView> (circular avatar)
├── <TextView> (title - "Hi, I'm Caleb")
├── <TextView> (message content)
├── <LinearLayout> (button container)
│   ├── <Button> (Visit Website)
│   └── <Button> (Email)
└── <ImageButton> (close X button)
```

## Data Models

### Popup State Model
```kotlin
data class PopupState(
    val firstInstallDate: Long,
    val lastPopupDate: Long,
    val versionIndex: Int, // 0-3, or -1 for complete
    val isComplete: Boolean
) {
    companion object {
        const val VERSION_COMPLETE = -1
        const val INITIAL_DELAY_MS = 5 * 24 * 60 * 60 * 1000L // 5 days
        const val REPEAT_INTERVAL_MS = 60 * 24 * 60 * 60 * 1000L // 60 days
    }
}
```

### Content Repository
```kotlin
object DeveloperNoteContentRepository {
    private val contentVariations = listOf(
        DeveloperNoteContent(
            version = 0,
            title = "Hi, I'm Caleb",
            message = "I built CameraClick as a side project, and it's grown into something that helps people every day. I'd love to keep working on meaningful projects like this full-time—if you know of opportunities, please visit my website."
        ),
        // ... other variations
    )
    
    fun getContentForVersion(version: Int): DeveloperNoteContent?
}
```

## Error Handling

### Graceful Degradation
- **SharedPreferences Failure**: Default to not showing popup rather than crashing
- **Image Loading Failure**: Show placeholder or text-only version
- **Intent Failure**: Log error and show toast message to user
- **Dialog Creation Failure**: Silently fail and continue app flow

### Edge Cases
- **Clock Changes**: Use elapsed time validation to prevent premature triggers
- **App Reinstall**: Treat as new installation with fresh timing
- **Rapid Dismissals**: Respect user choice and continue normal timing
- **Background State**: Only show when app is in foreground and active

### Error Recovery
```kotlin
try {
    // Popup display logic
} catch (e: Exception) {
    Log.e(TAG, "Error showing developer note popup", e)
    FirebaseCrashlytics.getInstance().recordException(e)
    // Continue normal app flow
}
```

## Testing Strategy

### Unit Tests
**DeveloperNoteManagerTest**:
- Timing calculation accuracy
- Version progression logic
- SharedPreferences state management
- Edge case handling (clock changes, invalid states)

**DeveloperNoteContentRepositoryTest**:
- Content retrieval for all versions
- Invalid version handling
- Content validation (non-empty strings, proper formatting)

### Integration Tests
**PopupDisplayTest**:
- DialogFragment lifecycle management
- UI element presence and styling
- Button click handling
- Animation behavior

**TimingIntegrationTest**:
- End-to-end timing flow from app launch to popup display
- State persistence across app restarts
- Multiple popup cycle completion

### UI Tests (Espresso)
**UserInteractionTest**:
- Popup appearance after timing conditions met
- Website button opens browser with correct URL
- Email button opens email client with correct recipient
- Close button dismisses popup and updates state
- Popup does not reappear until next interval

### Manual Testing Scenarios
1. **Fresh Install Flow**: Install app, verify 5-day delay, check first popup appearance
2. **Version Progression**: Manually advance time, verify each version shows in sequence
3. **Completion Cycle**: Verify no more popups after 4th version
4. **Interruption Handling**: Test popup behavior during app backgrounding/foregrounding
5. **Device Rotation**: Verify popup survives configuration changes

### Performance Testing
- **Memory Usage**: Verify DialogFragment properly releases resources
- **Startup Impact**: Measure timing check overhead on app launch
- **Image Loading**: Test avatar loading performance and caching

## Integration Points

### DeviceActivity Integration
The popup will be triggered from DeviceActivity.onCreate() after existing initialization:

```kotlin
// Add after existing initialization in onCreate()
private fun checkAndShowDeveloperNote() {
    if (DeveloperNoteManager.shouldShowPopup(this)) {
        DeveloperNoteManager.getNextPopupContent()?.let { content ->
            val dialog = DeveloperNoteDialogFragment.newInstance(content)
            dialog.show(supportFragmentManager, "developer_note")
        }
    }
}
```

### Analytics Integration
Following existing patterns with AnalyticsUtils:

```kotlin
// Track popup events
AnalyticsUtils.logFeatureUsage("developer_note", "popup_shown", version)
AnalyticsUtils.logFeatureUsage("developer_note", "website_clicked", version)
AnalyticsUtils.logFeatureUsage("developer_note", "email_clicked", version)
AnalyticsUtils.logFeatureUsage("developer_note", "popup_dismissed", version)
```

### Theme Integration
The popup will use existing app theme colors and styles:
- Primary color for action buttons
- Background colors matching current theme
- Typography consistent with existing text styles
- Elevation and shadows matching Material Design guidelines

## Technical Implementation Details

### Dependencies
No new dependencies required - using existing:
- AndroidX AppCompat for DialogFragment
- Material Design Components for styling
- Glide (if available) or standard ImageView for avatar
- Existing SharedPreferences patterns

### Resource Requirements
- **Avatar Image**: 120dp x 120dp circular headshot (drawable resource)
- **Layout Files**: Custom dialog layout with Material Design components
- **Animation Resources**: Fade in/out animations for smooth transitions
- **String Resources**: All text content in strings.xml for localization support

### Performance Considerations
- **Lazy Loading**: Manager only checks timing on app launch, not continuously
- **Efficient Storage**: Minimal SharedPreferences usage (3 keys total)
- **Memory Management**: DialogFragment automatically handles lifecycle cleanup
- **Image Optimization**: Avatar image optimized for multiple screen densities

### Security Considerations
- **URL Validation**: Hardcoded website URL prevents injection attacks
- **Email Validation**: Hardcoded email address prevents malicious redirects
- **Intent Safety**: Proper intent handling with try-catch for missing apps
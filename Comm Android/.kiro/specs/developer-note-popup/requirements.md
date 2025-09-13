# Requirements Document

## Introduction

The Developer Note Popup feature is designed to gently introduce users to the app's creator (Caleb) and provide opportunities for professional networking. The feature displays a periodic popup that cycles through 4 different messages over time, balancing visibility with user experience respect. The popup appears 5 days after initial install and then every 60 days thereafter, with each appearance showing a different variation until all 4 have been shown.

## Requirements

### Requirement 1

**User Story:** As a user, I want to learn about the app's developer in a non-intrusive way, so that I can appreciate the personal story behind the app without being overwhelmed by promotional content.

#### Acceptance Criteria

1. WHEN the app has been installed for 5 days THEN the system SHALL display the first developer note popup
2. WHEN a popup is displayed THEN the system SHALL show a centered DialogFragment with rounded corners and subtle shadow
3. WHEN a popup is displayed THEN the system SHALL include Caleb's circular headshot avatar at the top
4. WHEN a popup is displayed THEN the system SHALL show 2-3 sentences of body text that is centered and easily readable
5. WHEN a popup is displayed THEN the system SHALL provide two action buttons: "Visit My Website" and "Email"
6. WHEN a popup is displayed THEN the system SHALL provide a close button (✕) in the top-right corner

### Requirement 2

**User Story:** As a user, I want to have control over when I dismiss the popup, so that I can close it immediately if I'm not interested without being forced to make a permanent decision.

#### Acceptance Criteria

1. WHEN the user taps the ✕ button THEN the system SHALL close the popup with a fade-out animation
2. WHEN the popup is closed THEN the system SHALL NOT provide a "Don't show again" option
3. WHEN the popup is closed THEN the system SHALL record the dismissal timestamp for future scheduling
4. WHEN the popup appears THEN the system SHALL use fade-in animation for smooth presentation

### Requirement 3

**User Story:** As a user, I want the popup content to vary over time, so that I don't see the exact same message repeatedly and the experience feels fresh.

#### Acceptance Criteria

1. WHEN the first popup is shown THEN the system SHALL display Version 1 text focusing on introduction and project growth
2. WHEN the second popup is shown (60 days later) THEN the system SHALL display Version 2 text focusing on continued opportunity seeking
3. WHEN the third popup is shown (120 days later) THEN the system SHALL display Version 3 text focusing on community and usefulness
4. WHEN the fourth popup is shown (180 days later) THEN the system SHALL display Version 4 text focusing on personal persistence and scale
5. WHEN all four versions have been shown THEN the system SHALL NOT display any more popups
6. WHEN each popup version is shown THEN the system SHALL increment the version index in persistent storage

### Requirement 4

**User Story:** As a user, I want to easily visit the developer's website or send an email, so that I can learn more about opportunities or make contact if interested.

#### Acceptance Criteria

1. WHEN the user taps "Visit My Website" THEN the system SHALL open CalebSeely.com in the default browser
2. WHEN the user taps "Email" THEN the system SHALL open the default email client with CalebSeely@gmail.com as the recipient
3. WHEN either action button is tapped THEN the system SHALL close the popup after initiating the action
4. WHEN action buttons are displayed THEN the system SHALL make them clearly distinguishable and accessible

### Requirement 5

**User Story:** As a developer, I want the popup timing and state to be properly managed, so that the feature works reliably across app sessions and device restarts.

#### Acceptance Criteria

1. WHEN the app is first installed THEN the system SHALL record the installation timestamp in SharedPreferences
2. WHEN the app launches THEN the system SHALL check if popup display conditions are met based on stored timestamps
3. WHEN a popup is shown THEN the system SHALL update the lastPopupDate timestamp in SharedPreferences
4. WHEN a popup version is displayed THEN the system SHALL update the popupVersionIndex in SharedPreferences
5. WHEN popup state is managed THEN the system SHALL persist firstInstallDate, lastPopupDate, and popupVersionIndex values
6. WHEN checking display conditions THEN the system SHALL compare current time against stored timestamps using System.currentTimeMillis()

### Requirement 6

**User Story:** As a user, I want the popup to be implemented safely and efficiently, so that it doesn't cause crashes or performance issues in the app.

#### Acceptance Criteria

1. WHEN the popup is implemented THEN the system SHALL use DialogFragment for lifecycle safety
2. WHEN the avatar image is loaded THEN the system SHALL use Glide or Coil library for efficient image loading
3. WHEN the avatar is displayed THEN the system SHALL use CircleImageView for proper circular presentation
4. WHEN the popup is shown THEN the system SHALL handle configuration changes and activity lifecycle properly
5. WHEN browser intent is created THEN the system SHALL use Intent.ACTION_VIEW with Uri.parse for website opening
6. WHEN email intent is created THEN the system SHALL use Intent.ACTION_SENDTO with mailto URI scheme
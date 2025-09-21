# Google Play In-App Review Analytics Guide

## Overview
This guide shows you how to monitor the effectiveness of your Google Play In-App Review implementation using Google Analytics/Firebase Analytics data.

## Key Metrics to Track

### 1. Review Prompt Analytics Events

#### `in_app_review_ui_launch`
**What it means**: We requested the Google Play review UI
**Parameters**:
- `method`: "play_review_api"
- `stage`: "ui_launch_requested"
- `timestamp`: When the request was made
- `show_count`: How many times we've shown this user a review prompt

#### `in_app_review_ui_completion`
**What it means**: The Google Play review flow completed
**Parameters**:
- `method`: "play_review_api"
- `stage`: "ui_completed"
- `completion_time_ms`: How long the review flow took
- `timestamp`: When it completed
- `likely_suppressed`: `true` if completed suspiciously fast (<100ms)

#### `in_app_review`
**What it means**: Final result of the review attempt
**Parameters**:
- `method`: "play_review_api"
- `success`: `true`/`false`
- `timestamp`: When it finished
- `duration_ms`: Total time taken

#### `in_app_review_fallback`
**What it means**: Google Play API failed, showed fallback dialog
**Parameters**:
- `reason`: Error code from Google Play API
- `timestamp`: When fallback was triggered

#### `in_app_review_fallback_action`
**What it means**: User action in fallback dialog
**Parameters**:
- `action`: "rate_app", "not_now", or "dismiss"
- `timestamp`: When action was taken
- `marked_completed`: Whether this marked the review as complete

### 2. Feature Usage Events

#### `feature_usage` with `feature_name=in_app_review`
**What it means**: High-level review prompt tracking
**Parameters**:
- `action`: "prompt_shown" or "completed"
- `success`: `true`/`false`
- `timestamp`: When it happened

## How to Access This Data

### Option 1: Firebase Analytics Console

1. **Go to Firebase Console**
   - Visit [https://console.firebase.google.com](https://console.firebase.google.com)
   - Select your project

2. **Navigate to Analytics**
   - Click "Analytics" in the left sidebar
   - Go to "Events"

3. **Find Review Events**
   - Look for events starting with `in_app_review`
   - Click on each event to see parameters and user engagement

4. **Create Custom Reports**
   - Go to "Analysis" → "Funnel Analysis"
   - Set up funnel: `in_app_review_ui_launch` → `in_app_review_ui_completion`
   - This shows how many users see the UI vs complete it

### Option 2: Google Analytics 4 (if connected)

1. **Access GA4 Property**
   - Go to [https://analytics.google.com](https://analytics.google.com)
   - Select your app's property

2. **View Events**
   - Go to "Reports" → "Engagement" → "Events"
   - Search for events containing "review"

3. **Create Custom Exploration**
   - Go to "Explore" → "Free form"
   - Add events as metrics
   - Filter by event parameters

### Option 3: BigQuery Export (Advanced)

If you have BigQuery enabled:

```sql
-- Query to analyze review prompt effectiveness
SELECT 
  event_name,
  COUNT(*) as event_count,
  COUNTIF(event_params.key = 'likely_suppressed' AND event_params.value.string_value = 'true') as likely_suppressed_count
FROM `your-project.analytics_XXXXXX.events_*`
WHERE event_name LIKE '%in_app_review%'
  AND _TABLE_SUFFIX BETWEEN '20250901' AND '20250930'
GROUP BY event_name
ORDER BY event_count DESC
```

## Key Questions to Answer

### 1. Is the Review System Working?
**Metrics to check**:
- `in_app_review_ui_launch` count vs `in_app_review_ui_completion` count
- Average `completion_time_ms` 
- Percentage where `likely_suppressed = true`

**Red flags**:
- High launch count but very low completion count
- Most completions are <100ms (`likely_suppressed = true`)
- Many fallback events with error codes

### 2. Are Users Actually Seeing the UI?
**Metrics to check**:
- `completion_time_ms` distribution
- If most completions are >1000ms, users likely saw UI
- If most are <100ms, Google is suppressing the UI

### 3. What's the Conversion Rate?
**Formula**:
```
Conversion Rate = (Successful completions with >1000ms) / (Total launch attempts)
```

### 4. When Does Google Suppress the UI?
**Look for patterns**:
- Time of day
- User geography
- Device type
- App version
- Days since install

## Setting Up Alerts

### Firebase Analytics Alerts

1. **Go to Firebase Console** → Your Project → Analytics
2. **Click "Configure"** → "Notifications"
3. **Create Alert** for:
   - `in_app_review_ui_launch` drops significantly
   - High percentage of `likely_suppressed = true` events

### Google Analytics 4 Alerts

1. **Go to GA4** → Admin → Custom Definitions
2. **Create Custom Metrics** for review events
3. **Set up Intelligence Alerts** for anomalies

## Sample Analysis Queries

### Check if Reviews are Being Suppressed
```
Event: in_app_review_ui_completion
Filter: completion_time_ms < 100
Compare to: Total in_app_review_ui_completion events
```

### Find Optimal Timing
```
Events: in_app_review_ui_launch
Dimension: Days since install (custom parameter)
Metric: Conversion to completion >1000ms
```

### Geographic Effectiveness
```
Events: in_app_review_ui_completion
Dimension: Country
Filter: completion_time_ms > 1000
```

## Troubleshooting Common Issues

### High Launch, Low Completion
**Possible causes**:
- Google Play quota exceeded
- App not eligible for reviews
- User demographics don't match Google's criteria

### All Completions <100ms
**Likely cause**: Google is suppressing all UI
**Solution**: Check app quality metrics, user engagement

### No Events at All
**Possible causes**:
- Review logic not being triggered
- Analytics not properly configured
- Timing conditions not met

## Weekly Monitoring Checklist

- [ ] Check total `in_app_review_ui_launch` events
- [ ] Calculate completion rate (>1000ms completions / launches)
- [ ] Monitor `likely_suppressed` percentage
- [ ] Review any `in_app_review_fallback` events
- [ ] Check geographic distribution of successful reviews
- [ ] Compare to previous week for trends

## Expected Benchmarks

Based on industry data:
- **UI Suppression Rate**: 30-70% (Google's quotas)
- **User Completion Rate**: 1-5% of eligible users
- **Fallback Usage**: <10% in production apps

Remember: Google's In-App Review API is intentionally restrictive to prevent spam, so lower rates are normal and expected.

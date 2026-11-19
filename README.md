# Face Detail & Skin Analysis App

A demo-ready face and skin analysis project available for Android and modern browsers.

## Features

- Android Java application with CameraX
- ML Kit face detection and contour analysis
- Browser/PWA camera and gallery capture
- RGB/HSV cosmetic pixel estimates
- Required name and age profile fields
- Local browser analysis history
- Glow, moisture, pigmentation, blemish, eyebrow, lip, and mustache estimates
- Periodic progress chart
- Print/save PDF report support
- Spring Boot report logging endpoint
- Public HTTPS deployment support

## Android

Open this repository in Android Studio. The Android module is in `app/`.

## Browser Demo

```powershell
cd backend
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Backend

The Spring Boot backend is in `backend/`. Run:

```powershell
mvn spring-boot:run
```

Report endpoint:

```text
POST /api/save-report
```

## Privacy

Face image processing is performed locally on the browser/device. This application provides cosmetic estimates and is not a medical diagnosis.

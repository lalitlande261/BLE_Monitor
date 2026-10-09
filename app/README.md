    # BLE Monitor

An Android application developed using Kotlin and Jetpack Compose to demonstrate Bluetooth Low Energy (BLE) scanning, device connection, battery monitoring, foreground services, and Android Accessibility Service.

## Features

* Scan for nearby BLE devices.
* Display device name, MAC address, and RSSI.
* Connect to and disconnect from BLE devices.
* Read battery level when the connected device exposes the standard BLE Battery Service.
* Run BLE monitoring using a foreground service with a persistent notification.
* Handle Bluetooth permissions and Bluetooth being disabled.
* Demonstrate Android Accessibility Service events after the user enables the service in Settings.

## Tech Stack

* Kotlin
* Jetpack Compose
* Material 3
* Android Bluetooth LE APIs
* Android Foreground Service
* Android Accessibility Service
* Kotlin Coroutines and StateFlow, where used

## Requirements

* Android Studio
* Android device with Bluetooth Low Energy support, or a compatible emulator for the features it supports
* Android SDK versions configured in the project

## Setup

1. Clone or download the project repository.
2. Open the project in Android Studio.
3. Allow Gradle synchronization to finish.
4. Connect an Android device and enable Bluetooth.
5. Run the application.
6. Grant the requested Bluetooth and notification permissions.
7. To test Accessibility Service, open the Accessibility screen and enable BLE Monitor in Android Settings.

## How to Test

1. Open BLE Monitor and tap **Scan for Devices**.
2. Verify that nearby BLE devices appear with their identifiers and RSSI.
3. Select a compatible BLE device and test connection and disconnection.
4. If the device supports the standard BLE Battery Service, check whether its battery level is displayed.
5. Verify the foreground-service notification while monitoring is active.
6. Enable Accessibility Service and verify that supported events appear in the app.
7. Test Bluetooth being disabled and permission-denied scenarios.

## Implementation Notes

BLE device discovery and battery-level availability depend on the nearby hardware and its supported GATT services. Background execution and reconnection behavior may vary by Android version and device manufacturer.

Accessibility events can contain sensitive information. Event collection should be limited to what is necessary for the demonstration and should be used transparently.

## Deliverables

* Android Studio project
* APK build
* README documentation
* Short demonstration video
* Git repository link

## Author

LALIT LANDE
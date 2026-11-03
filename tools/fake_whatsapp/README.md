# Atrapa WhatsApp do testu E2E

Aplikacja o pakiecie `com.whatsapp`, która udaje trwające połączenie: usługa pierwszoplanowa z mikrofonem
`VOICE_COMMUNICATION`, tryb audio `MODE_IN_COMMUNICATION` i powiadomienie z kategorią `CATEGORY_CALL`.
Tylko na emulator (na telefonie z prawdziwym WhatsAppem instalacja się nie uda, bo pakiet jest zajęty).

```bash
cd tools/fake_whatsapp && ./gradlew assembleDebug          # potrzebny local.properties z sdk.dir albo ANDROID_HOME
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure enabled_accessibility_services pl.voxverum.app/pl.voxverum.app.call.VoxAccessibilityService
adb shell svc power stayon true                            # uśpiony ekran kończył atrapę rozmowy
adb shell am start -n com.whatsapp/.CallActivity            # początek rozmowy
adb logcat -s VoxVerum                                     # Call state -> Active, Recording from ..., okno ...
adb shell am stopservice com.whatsapp/.CallService          # koniec rozmowy: Call state -> Idle, Mic released
```
W trakcie testu nie używaj `uiautomator dump`. UiAutomation na chwilę wyłącza inne usługi ułatwień dostępu,
więc VoxVerum kończy wtedy rozmowę. Do klikania używaj `screencap` i `input tap`.

# A2DP AutoRouter — v2026.11.06-alpha.1

**Type:** Pre-release · **Date:** 2026-11-06 · **Stage:** Alpha 1

Перша публічна експериментальна версія A2DP AutoRouter з Android Control та ZygiskFrida/Frida Gadget backend.

### Works in verified setup

- Google Meet → Sony WH-CH720N via A2DP (voice communication output → MEDIA).
- Spotify → same A2DP output simultaneously.
- Google Meet microphone remains the phone microphone.
- Autostart after reboot via Magisk + ZygiskFrida; GUI toggles routing without a PC.

**Tested:** Motorola moto g54 5G, Android 15, Sony WH-CH720N; Bluetooth media profile enabled, phone-calls/HFP disabled.

### Alpha caveats

- Root, Magisk, ZygiskFrida and its Frida Gadget binary are prerequisites; not included in APK.
- Only Meet and Spotify and newly opened AAudio output streams are supported.
- Backend installer on clean devices and GitHub Actions APK build have not yet been end-to-end tested in this packaging.
- The attached APK (if present) is a **debug** build with ephemeral signing; it may not update an existing independently signed installation.

Full source, build steps and safety notes are in [README.md](README.md).

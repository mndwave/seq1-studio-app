import { CapacitorConfig } from '@capacitor/cli';

// Bump this when making a native change that requires an APK rebuild.
// Format: MAJOR.MINOR.PATCH — Obtainium uses this to detect updates.
export const APP_VERSION = '2.4.2';

const config: CapacitorConfig = {
  appId: 'net.seq1.studio',
  appName: 'SEQ1 Studio',
  webDir: 'www',
  server: {
    // Server URL mode: loads the live web app rather than bundled assets.
    // This means web deploys update the app instantly — no APK rebuild needed.
    // Only rebuild the APK when native plugins or AndroidManifest change.
    url: 'https://studio.seq1.net',
    cleartext: false,
    androidScheme: 'https',
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
    // Prevent overscroll glow/bounce effect — this is an app, not a webpage.
    overScrollMode: 'never',
    // Cap 8: edge-to-edge is always enabled. studio.seq1.net handles system bar insets
    // via CSS env(safe-area-inset-*) which Capacitor 8 correctly passes through.
    // adjustMarginsForEdgeToEdge removed from Cap 8 API — CSS env() approach is correct.
  },
  plugins: {
    StatusBar: {
      // Dark = white status bar icons (correct for stone-950 header).
      // overlaysWebView: true = status bar is transparent overlay so
      // env(safe-area-inset-top) returns the real status bar height and
      // body padding-top (set by NativeAppShell + globals.css) pushes
      // content below it. NativeAppShell also calls setOverlaysWebView
      // at runtime so existing APKs without this config entry still work.
      style: 'Dark',
      overlaysWebView: true,
    },
    SplashScreen: {
      // Disabled: launchShowDuration 0 means the native splash is never shown.
      // The AppLoader in studio.seq1.net handles the entire loading animation
      // in the web layer — no native/web handoff, no immersive bar flash.
      // backgroundColor still sets the WebView background colour so there is no
      // white flash while the WebView initialises before the first paint.
      backgroundColor: '#F4F1E8',
      launchShowDuration: 0,
      autoHide: true,
    },
    LocalNotifications: {
      smallIcon: 'ic_stat_icon_config_sample',
      iconColor: '#14b8a6',
      sound: 'beep.wav',
      channels: [
        {
          id: 'voice-delivery',
          name: 'Voice Transcription',
          description: 'Delivered when a voice recording has been transcribed',
          importance: 4,
          visibility: 1,
          vibration: true,
        },
      ],
    },
  },
};

export default config;

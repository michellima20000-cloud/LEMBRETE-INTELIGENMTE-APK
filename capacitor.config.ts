import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.lembreteinteligente.app',
  appName: 'Lembrete Inteligente',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    cleartext: true
  },
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_icon_config',
      iconColor: '#4f46e5',
      sound: 'alarm_loud.wav'
    }
  }
};

export default config;

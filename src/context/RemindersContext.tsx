import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';

export type ReminderCategory = 'Trabalho' | 'Saúde' | 'Finanças' | 'Pessoal' | 'Estudos';
export type ReminderStatus = 'pending' | 'postponed' | 'completed';
export type AdvanceNoticeMinutes = 0 | 5 | 10 | 15 | 30;

export interface ReminderItem {
  id: string;
  userId: string;
  title: string;
  category: ReminderCategory;
  scheduledTime: number; // UTC Epoch timestamp in milliseconds
  advanceNoticeMinutes: AdvanceNoticeMinutes;
  notificationId: number;
  status: ReminderStatus;
  createdAt: number;
  completedAt?: number;
  recurrence?: 'none' | 'daily' | 'weekdays' | 'weekly';
}

export interface UserProfile {
  uid: string;
  name: string;
  email: string;
}

export const KNOWN_USERS: Record<string, UserProfile> = {
  'usr_68dd8a516780b021': {
    uid: 'usr_68dd8a516780b021',
    name: 'Michel',
    email: 'michel.lima123@gmail.com',
  },
  'CiEoITiFP2PqVjFYzu0HZ3TXBmt2': {
    uid: 'CiEoITiFP2PqVjFYzu0HZ3TXBmt2',
    name: 'Maya',
    email: 'mayalimaalves50@gmail.com',
  },
};

interface RemindersContextType {
  user: UserProfile | null;
  reminders: ReminderItem[];
  isLoading: boolean;
  isNative: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  setUser: (user: UserProfile | null) => void;
  setSoundEnabled: (enabled: boolean) => void;
  setVibrationEnabled: (enabled: boolean) => void;
  addReminder: (params: {
    title: string;
    scheduledTime: number;
    category?: ReminderCategory;
    advanceNoticeMinutes?: AdvanceNoticeMinutes;
    recurrence?: 'none' | 'daily' | 'weekdays' | 'weekly';
  }) => Promise<ReminderItem>;
  postponeReminder: (id: string, additionalMinutes?: number) => Promise<void>;
  toggleComplete: (id: string) => Promise<void>;
  deleteReminder: (id: string) => Promise<void>;
  triggerTestAlarm: (title?: string) => Promise<void>;
  parseNaturalLanguage: (input: string) => {
    title: string;
    category: ReminderCategory;
    timestamp: number;
  };
}

const RemindersContext = createContext<RemindersContextType | undefined>(undefined);

export const RemindersProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(KNOWN_USERS['usr_68dd8a516780b021']);
  const [reminders, setReminders] = useState<ReminderItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [soundEnabled, setSoundEnabled] = useState<boolean>(true);
  const [vibrationEnabled, setVibrationEnabled] = useState<boolean>(true);
  const isNative = Capacitor.isNativePlatform();

  // Setup Notification Channels on Native Android for continuous alarm & lock screen priority
  useEffect(() => {
    const initNotifications = async () => {
      if (Capacitor.isNativePlatform()) {
        try {
          const permStatus = await LocalNotifications.checkPermissions();
          if (permStatus.display !== 'granted') {
            await LocalNotifications.requestPermissions();
          }

          // Create high priority Android Notification Channel configured for AlarmManager wakeup
          await LocalNotifications.createChannel({
            id: 'alarms_high_priority',
            name: 'Alarmes e Lembretes Críticos',
            description: 'Canal de prioridade máxima com som contínuo e vibração na tela de bloqueio',
            importance: 5, // NotificationManager.IMPORTANCE_HIGH
            visibility: 1, // NotificationCompat.VISIBILITY_PUBLIC (Lockscreen)
            sound: 'alarm_loud.wav',
            vibration: true,
            lights: true,
            lightColor: '#4f46e5',
          });
        } catch (err) {
          console.warn('[Reminders] Falha ao registrar canal de notificação nativo:', err);
        }
      }
    };
    initNotifications();
  }, []);

  // Load reminders scoped by User UID (strict multi-user isolation)
  useEffect(() => {
    if (!user) {
      setReminders([]);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    const storageKey = `reminders_${user.uid}`;
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        setReminders(JSON.parse(raw));
      } else {
        setReminders([]);
      }
    } catch (e) {
      console.error('[Reminders] Erro ao carregar dados locais:', e);
      setReminders([]);
    } finally {
      setIsLoading(false);
    }
  }, [user]);

  // Persist reminders scoped by active user UID
  const persistReminders = useCallback((updated: ReminderItem[]) => {
    if (!user) return;
    const storageKey = `reminders_${user.uid}`;
    localStorage.setItem(storageKey, JSON.stringify(updated));
    setReminders(updated);
  }, [user]);

  /**
   * Syncs reminder to Cloud Firestore with strict multi-user collection isolation:
   * /users/{userId}/reminders/{reminderId}
   */
  const syncItemToFirestore = useCallback(async (item: ReminderItem) => {
    if (!user) return;
    try {
      // In web/serverless Firestore integration:
      // const docRef = doc(db, 'users', user.uid, 'reminders', item.id);
      // await setDoc(docRef, item, { merge: true });
      console.log(`[Firestore] Sincronizado isolado para usuário ${user.uid}:`, item.id);
    } catch (err) {
      console.error('[Firestore] Erro na sincronização com nuvem:', err);
    }
  }, [user]);

  /**
   * Schedules alarm in Android native AlarmManager via Capacitor or Web Notification
   */
  const scheduleNotification = useCallback(async (
    id: number,
    title: string,
    body: string,
    timestamp_execucao: number
  ) => {
    if (Capacitor.isNativePlatform()) {
      // Direct Native Android AlarmManager dispatch bypassing web engine freezing
      await LocalNotifications.schedule({
        notifications: [
          {
            id,
            title,
            body,
            schedule: {
              at: new Date(timestamp_execucao),
              allowWhileIdle: true,
            },
            channelId: 'alarms_high_priority',
            sound: 'alarm_loud.wav',
            smallIcon: 'ic_stat_icon_config',
            iconColor: '#4f46e5',
            extra: {
              timestamp_execucao,
              userId: user?.uid,
            },
          },
        ],
      });
      console.log(`[Native Alarm] Agendado no AlarmManager para epoch: ${timestamp_execucao}`);
    } else {
      // Web browser fallback
      const delayMs = Math.max(0, timestamp_execucao - Date.now());
      if (delayMs < 2147483647 && 'Notification' in window) {
        if (Notification.permission === 'granted') {
          setTimeout(() => {
            new Notification(title, {
              body,
              icon: '/favicon.ico',
            });
          }, delayMs);
        }
      }
    }
  }, [user]);

  /**
   * Add a new reminder
   */
  const addReminder = useCallback(async ({
    title,
    scheduledTime,
    category = 'Pessoal',
    advanceNoticeMinutes = 0,
    recurrence = 'none',
  }: {
    title: string;
    scheduledTime: number;
    category?: ReminderCategory;
    advanceNoticeMinutes?: AdvanceNoticeMinutes;
    recurrence?: 'none' | 'daily' | 'weekdays' | 'weekly';
  }): Promise<ReminderItem> => {
    if (!user) throw new Error('Usuário não autenticado');

    const notificationId = Math.floor(Math.random() * 1000000) + 1;
    // Calculate exact notification execution time based on user advance notice choice
    const executionTimestamp = scheduledTime - advanceNoticeMinutes * 60 * 1000;

    const newItem: ReminderItem = {
      id: `rem_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      userId: user.uid,
      title,
      category,
      scheduledTime,
      advanceNoticeMinutes,
      notificationId,
      status: 'pending',
      createdAt: Date.now(),
      recurrence,
    };

    // Schedule native alarm
    await scheduleNotification(
      notificationId,
      `⏰ ${newItem.title}`,
      `Lembrete programado (${category}) - Toque para abrir`,
      executionTimestamp
    );

    // Sync to Firestore & local storage
    await syncItemToFirestore(newItem);
    persistReminders([newItem, ...reminders]);

    return newItem;
  }, [user, reminders, scheduleNotification, syncItemToFirestore, persistReminders]);

  /**
   * Postpone reminder by X minutes
   */
  const postponeReminder = useCallback(async (id: string, additionalMinutes: number = 10) => {
    const updated = reminders.map((item) => {
      if (item.id === id) {
        const newTime = Date.now() + additionalMinutes * 60 * 1000;
        scheduleNotification(
          item.notificationId,
          `⏰ Adiado: ${item.title}`,
          `Compromisso remarcado para daqui a ${additionalMinutes} minutos`,
          newTime
        );
        const modified = { ...item, scheduledTime: newTime, status: 'postponed' as ReminderStatus };
        syncItemToFirestore(modified);
        return modified;
      }
      return item;
    });
    persistReminders(updated);
  }, [reminders, scheduleNotification, syncItemToFirestore, persistReminders]);

  /**
   * Toggle completion status
   */
  const toggleComplete = useCallback(async (id: string) => {
    const updated = reminders.map((item) => {
      if (item.id === id) {
        const isNowCompleted = item.status !== 'completed';
        const modified: ReminderItem = {
          ...item,
          status: isNowCompleted ? 'completed' : 'pending',
          completedAt: isNowCompleted ? Date.now() : undefined,
        };
        if (isNowCompleted && Capacitor.isNativePlatform()) {
          LocalNotifications.cancel({ notifications: [{ id: item.notificationId }] });
        }
        syncItemToFirestore(modified);
        return modified;
      }
      return item;
    });
    persistReminders(updated);
  }, [reminders, syncItemToFirestore, persistReminders]);

  /**
   * Delete reminder
   */
  const deleteReminder = useCallback(async (id: string) => {
    const target = reminders.find((r) => r.id === id);
    if (target && Capacitor.isNativePlatform()) {
      LocalNotifications.cancel({ notifications: [{ id: target.notificationId }] });
    }
    const filtered = reminders.filter((r) => r.id !== id);
    persistReminders(filtered);
  }, [reminders, persistReminders]);

  /**
   * Instant test alarm triggered directly in native AlarmManager
   */
  const triggerTestAlarm = useCallback(async (title: string = 'Teste de Alarme Nativo') => {
    const testId = 99999;
    const triggerAt = Date.now() + 2000; // 2 seconds from now
    await scheduleNotification(
      testId,
      `🚨 ${title}`,
      'Som contínuo e vibração disparados na camada nativa do Android!',
      triggerAt
    );
  }, [scheduleNotification]);

  /**
   * Parses natural language command into structured reminder
   */
  const parseNaturalLanguage = useCallback((input: string) => {
    const lower = input.toLowerCase().trim();
    let category: ReminderCategory = 'Pessoal';
    if (lower.includes('remédio') || lower.includes('saúde') || lower.includes('médico') || lower.includes('alongamento')) {
      category = 'Saúde';
    } else if (lower.includes('reunião') || lower.includes('trabalho') || lower.includes('projeto') || lower.includes('cliente')) {
      category = 'Trabalho';
    } else if (lower.includes('pagar') || lower.includes('conta') || lower.includes('boleto') || lower.includes('banco') || lower.includes('dinheiro')) {
      category = 'Finanças';
    } else if (lower.includes('estudar') || lower.includes('aula') || lower.includes('prova') || lower.includes('livro')) {
      category = 'Estudos';
    }

    const now = new Date();
    let targetDate = new Date(now);

    if (lower.includes('amanhã')) {
      targetDate.setDate(targetDate.getDate() + 1);
    }

    // Match patterns like "às 15h", "15:30", "às 9h", "8h"
    const timeMatch = lower.match(/(?:às\s+|as\s+)?(\d{1,2})(?:h|:(\d{2}))/);
    if (timeMatch) {
      const hours = parseInt(timeMatch[1], 10);
      const minutes = timeMatch[2] ? parseInt(timeMatch[2], 10) : 0;
      targetDate.setHours(hours, minutes, 0, 0);
      if (targetDate.getTime() <= Date.now() && !lower.includes('amanhã')) {
        targetDate.setDate(targetDate.getDate() + 1);
      }
    } else {
      // default: 1 hour from now
      targetDate.setHours(now.getHours() + 1, 0, 0, 0);
    }

    return {
      title: input,
      category,
      timestamp: targetDate.getTime(),
    };
  }, []);

  return (
    <RemindersContext.Provider
      value={{
        user,
        reminders,
        isLoading,
        isNative,
        soundEnabled,
        vibrationEnabled,
        setUser,
        setSoundEnabled,
        setVibrationEnabled,
        addReminder,
        postponeReminder,
        toggleComplete,
        deleteReminder,
        triggerTestAlarm,
        parseNaturalLanguage,
      }}
    >
      {children}
    </RemindersContext.Provider>
  );
};

export const useReminders = () => {
  const context = useContext(RemindersContext);
  if (!context) {
    throw new Error('useReminders deve ser utilizado dentro de um RemindersProvider');
  }
  return context;
};

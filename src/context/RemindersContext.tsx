import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
  onAuthStateChanged,
  User as FirebaseUser
} from 'firebase/auth';
import {
  collection,
  doc,
  setDoc,
  deleteDoc,
  onSnapshot
} from 'firebase/firestore';
import { auth, db } from '../firebase';

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
  signUpWithEmail: (email: string, pass: string, name: string) => Promise<void>;
  signInWithEmail: (email: string, pass: string) => Promise<void>;
  signOutUser: () => Promise<void>;
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
  isRinging: boolean;
  activeAlarmTitle: string | null;
  stopAlarm: () => void;
  loginAsDemo: (name: string, email: string, uid?: string) => void;
  triggerTestAlarm: (title?: string) => Promise<void>;
  parseNaturalLanguage: (input: string) => {
    title: string;
    category: ReminderCategory;
    timestamp: number;
  };
}

const RemindersContext = createContext<RemindersContextType | undefined>(undefined);

export const RemindersProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Start with null - no pre-filled default account
  const [user, setUser] = useState<UserProfile | null>(() => {
    try {
      const saved = localStorage.getItem('auth_user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  const [reminders, setReminders] = useState<ReminderItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [soundEnabled, setSoundEnabled] = useState<boolean>(true);
  const [vibrationEnabled, setVibrationEnabled] = useState<boolean>(true);
  const [isRinging, setIsRinging] = useState<boolean>(false);
  const [activeAlarmTitle, setActiveAlarmTitle] = useState<string | null>(null);
  const audioInstanceRef = React.useRef<HTMLAudioElement | null>(null);
  const isNative = Capacitor.isNativePlatform();

  // Listen to Firebase Auth state
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (firebaseUser: FirebaseUser | null) => {
      if (firebaseUser) {
        const profile: UserProfile = {
          uid: firebaseUser.uid,
          name: firebaseUser.displayName || firebaseUser.email?.split('@')[0] || 'Usuário',
          email: firebaseUser.email || '',
        };
        setUser(profile);
        localStorage.setItem('auth_user', JSON.stringify(profile));
      } else {
        // If not authenticated in Firebase, clear user
        setUser(null);
        localStorage.removeItem('auth_user');
      }
      setIsLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // Setup Notification Channels on Native Android
  useEffect(() => {
    const initNotifications = async () => {
      if (Capacitor.isNativePlatform()) {
        try {
          const permStatus = await LocalNotifications.checkPermissions();
          if (permStatus.display !== 'granted') {
            await LocalNotifications.requestPermissions();
          }

          await LocalNotifications.createChannel({
            id: 'alarms_high_priority',
            name: 'Alarmes e Lembretes Críticos',
            description: 'Canal de prioridade máxima com som contínuo e vibração na tela de bloqueio',
            importance: 5,
            visibility: 1,
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

  // Real-time Firestore sync & local persistence scoped by user UID
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
        const seedItems: ReminderItem[] = [
          {
            id: `rem_demo_1_${user.uid}`,
            userId: user.uid,
            title: 'Tomar remédio para pressão',
            category: 'Saúde',
            scheduledTime: Date.now() + 3600 * 1000,
            advanceNoticeMinutes: 10,
            notificationId: 101,
            status: 'pending',
            createdAt: Date.now() - 3600000,
            recurrence: 'daily'
          },
          {
            id: `rem_demo_2_${user.uid}`,
            userId: user.uid,
            title: 'Reunião de alinhamento com equipe',
            category: 'Trabalho',
            scheduledTime: Date.now() + 86400 * 1000,
            advanceNoticeMinutes: 15,
            notificationId: 102,
            status: 'pending',
            createdAt: Date.now() - 7200000,
            recurrence: 'none'
          },
          {
            id: `rem_demo_3_${user.uid}`,
            userId: user.uid,
            title: 'Pagar fatura do cartão de crédito',
            category: 'Finanças',
            scheduledTime: Date.now() + 172800 * 1000,
            advanceNoticeMinutes: 30,
            notificationId: 103,
            status: 'pending',
            createdAt: Date.now() - 10800000,
            recurrence: 'none'
          }
        ];
        setReminders(seedItems);
        localStorage.setItem(storageKey, JSON.stringify(seedItems));
      }
    } catch (e) {
      console.warn('[Reminders] Erro cache local:', e);
    }

    // Subscribe to user's private Firestore subcollection: /users/{userId}/reminders
    try {
      const remindersCol = collection(db, 'users', user.uid, 'reminders');
      const unsubscribeFirestore = onSnapshot(
        remindersCol,
        (snapshot) => {
          const items: ReminderItem[] = [];
          snapshot.forEach((docSnap) => {
            items.push(docSnap.data() as ReminderItem);
          });
          // Sort chronologically
          items.sort((a, b) => a.scheduledTime - b.scheduledTime);
          setReminders(items);
          localStorage.setItem(storageKey, JSON.stringify(items));
          setIsLoading(false);
        },
        (err) => {
          console.warn('[Firestore] Usando modo local para subcoleção do usuário:', err.message);
          setIsLoading(false);
        }
      );

      return () => unsubscribeFirestore();
    } catch {
      setIsLoading(false);
    }
  }, [user]);

  // Auth: Sign Up with Email and Password
  const signUpWithEmail = async (email: string, pass: string, name: string) => {
    const cred = await createUserWithEmailAndPassword(auth, email, pass);
    if (name.trim()) {
      await updateProfile(cred.user, { displayName: name.trim() });
    }
    const profile: UserProfile = {
      uid: cred.user.uid,
      name: name.trim() || cred.user.email?.split('@')[0] || 'Usuário',
      email: cred.user.email || email,
    };
    setUser(profile);
    localStorage.setItem('auth_user', JSON.stringify(profile));
  };

  // Auth: Sign In with Email and Password
  const signInWithEmail = async (email: string, pass: string) => {
    const cred = await signInWithEmailAndPassword(auth, email, pass);
    const profile: UserProfile = {
      uid: cred.user.uid,
      name: cred.user.displayName || cred.user.email?.split('@')[0] || 'Usuário',
      email: cred.user.email || email,
    };
    setUser(profile);
    localStorage.setItem('auth_user', JSON.stringify(profile));
  };

  // Auth: Quick Demo Login (Michel / Maya)
  const loginAsDemo = useCallback((name: string, email: string, uid?: string) => {
    const defaultUid = uid || (email.toLowerCase().includes('michel') ? 'usr_68dd8a516780b021' : 'CiEoITiFP2PqVjFYzu0HZ3TXBmt2');
    const profile: UserProfile = {
      uid: defaultUid,
      name,
      email,
    };
    setUser(profile);
    localStorage.setItem('auth_user', JSON.stringify(profile));
  }, []);

  // Auth: Sign Out
  const signOutUser = async () => {
    stopAlarm();
    await signOut(auth);
    setUser(null);
    setReminders([]);
    localStorage.removeItem('auth_user');
  };

  // Persist reminders to LocalStorage & Firestore
  const persistReminders = useCallback((updated: ReminderItem[]) => {
    if (!user) return;
    const storageKey = `reminders_${user.uid}`;
    localStorage.setItem(storageKey, JSON.stringify(updated));
    setReminders(updated);
  }, [user]);

  // Schedules alarm in Android native AlarmManager via LocalNotifications
  const scheduleNotification = useCallback(async (
    id: number,
    title: string,
    body: string,
    timestamp_execucao: number
  ) => {
    if (Capacitor.isNativePlatform()) {
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
    } else {
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

  // Add a new reminder
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

    const updated = [newItem, ...reminders].sort((a, b) => a.scheduledTime - b.scheduledTime);
    persistReminders(updated);

    // Save to Cloud Firestore
    try {
      const docRef = doc(db, 'users', user.uid, 'reminders', newItem.id);
      await setDoc(docRef, newItem, { merge: true });
    } catch (e) {
      console.warn('[Firestore] Salvo localmente; sincronizará quando online:', e);
    }

    // Schedule notification
    if (executionTimestamp > Date.now()) {
      await scheduleNotification(
        notificationId,
        `Lembrete: ${title}`,
        advanceNoticeMinutes > 0
          ? `Compromisso em ${advanceNoticeMinutes} minutos!`
          : `Chegou a hora do seu compromisso!`,
        executionTimestamp
      );
    }

    return newItem;
  }, [user, reminders, persistReminders, scheduleNotification]);

  // Stop active ringing alarm
  const stopAlarm = useCallback(() => {
    setIsRinging(false);
    setActiveAlarmTitle(null);
    if (audioInstanceRef.current) {
      audioInstanceRef.current.pause();
      audioInstanceRef.current.currentTime = 0;
    }
  }, []);

  // Postpone reminder
  const postponeReminder = useCallback(async (id: string, additionalMinutes: number = 10) => {
    if (!user) return;
    const item = reminders.find((r) => r.id === id);
    if (!item) return;

    const newScheduledTime = Date.now() + additionalMinutes * 60 * 1000;
    const updatedItem: ReminderItem = {
      ...item,
      scheduledTime: newScheduledTime,
      status: 'postponed',
    };

    const updated = reminders.map((r) => (r.id === id ? updatedItem : r)).sort((a, b) => a.scheduledTime - b.scheduledTime);
    persistReminders(updated);

    try {
      const docRef = doc(db, 'users', user.uid, 'reminders', id);
      await setDoc(docRef, updatedItem, { merge: true });
    } catch (e) {
      console.warn('[Firestore] Atualizado localmente:', e);
    }

    await scheduleNotification(
      item.notificationId,
      `[Adiado] Lembrete: ${item.title}`,
      `Compromisso adiado por ${additionalMinutes} minutos.`,
      newScheduledTime
    );
    stopAlarm();
  }, [user, reminders, persistReminders, scheduleNotification, stopAlarm]);

  // Toggle complete
  const toggleComplete = useCallback(async (id: string) => {
    if (!user) return;
    const item = reminders.find((r) => r.id === id);
    if (!item) return;

    const newStatus: ReminderStatus = item.status === 'completed' ? 'pending' : 'completed';
    const updatedItem: ReminderItem = {
      ...item,
      status: newStatus,
      completedAt: newStatus === 'completed' ? Date.now() : undefined,
    };

    const updated = reminders.map((r) => (r.id === id ? updatedItem : r));
    persistReminders(updated);

    try {
      const docRef = doc(db, 'users', user.uid, 'reminders', id);
      await setDoc(docRef, updatedItem, { merge: true });
    } catch (e) {
      console.warn('[Firestore] Atualizado localmente:', e);
    }

    if (newStatus === 'completed' && Capacitor.isNativePlatform()) {
      try {
        await LocalNotifications.cancel({ notifications: [{ id: item.notificationId }] });
      } catch {}
    }
  }, [user, reminders, persistReminders]);

  // Delete reminder
  const deleteReminder = useCallback(async (id: string) => {
    if (!user) return;
    const item = reminders.find((r) => r.id === id);
    const updated = reminders.filter((r) => r.id !== id);
    persistReminders(updated);

    try {
      const docRef = doc(db, 'users', user.uid, 'reminders', id);
      await deleteDoc(docRef);
    } catch (e) {
      console.warn('[Firestore] Excluído localmente:', e);
    }

    if (item && Capacitor.isNativePlatform()) {
      try {
        await LocalNotifications.cancel({ notifications: [{ id: item.notificationId }] });
      } catch {}
    }
  }, [user, reminders, persistReminders]);

  // Trigger test alarm
  const triggerTestAlarm = useCallback(async (title = 'Alarme de Alta Prioridade') => {
    setIsRinging(true);
    setActiveAlarmTitle(title);
    const testTime = Date.now() + 2000;
    const notifId = 99999;
    await scheduleNotification(
      notifId,
      `🔔 ${title}`,
      'Disparo de alarme com som contínuo e prioridade máxima!',
      testTime
    );

    // Audio playback for browser with real audio file or oscillator fallback
    if (soundEnabled) {
      try {
        if (!audioInstanceRef.current) {
          audioInstanceRef.current = new Audio('/alarm_loud.wav');
        }
        audioInstanceRef.current.loop = true;
        audioInstanceRef.current.currentTime = 0;
        audioInstanceRef.current.play().catch(() => {
          const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          osc.type = 'sawtooth';
          osc.frequency.setValueAtTime(880, audioCtx.currentTime);
          osc.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.4);
          gain.gain.setValueAtTime(0.3, audioCtx.currentTime);
          gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.8);
          osc.connect(gain);
          gain.connect(audioCtx.destination);
          osc.start();
          osc.stop(audioCtx.currentTime + 0.8);
        });
      } catch (err) {
        console.warn('Audio playback error:', err);
      }
    }
  }, [scheduleNotification, soundEnabled]);

  // Natural Language Parser
  const parseNaturalLanguage = useCallback((input: string): {
    title: string;
    category: ReminderCategory;
    timestamp: number;
    hour: number;
    minute: number;
    isTomorrow: boolean;
  } => {
    const raw = input.trim();
    const lower = raw.toLowerCase();
    let category: ReminderCategory = 'Pessoal';

    if (lower.includes('remédio') || lower.includes('remedio') || lower.includes('médico') || lower.includes('medico') || lower.includes('saúde') || lower.includes('saude') || lower.includes('dentista') || lower.includes('consulta') || lower.includes('treino') || lower.includes('água') || lower.includes('agua') || lower.includes('pressão') || lower.includes('exame')) {
      category = 'Saúde';
    } else if (lower.includes('reunião') || lower.includes('reuniao') || lower.includes('trabalho') || lower.includes('projeto') || lower.includes('cliente') || lower.includes('call') || lower.includes('apresentação') || lower.includes('relatório') || lower.includes('equipe') || lower.includes('chefe')) {
      category = 'Trabalho';
    } else if (lower.includes('pagar') || lower.includes('pagamento') || lower.includes('boleto') || lower.includes('conta') || lower.includes('fatura') || lower.includes('pix') || lower.includes('banco') || lower.includes('aluguel') || lower.includes('cartão')) {
      category = 'Finanças';
    } else if (lower.includes('estudar') || lower.includes('estudo') || lower.includes('aula') || lower.includes('prova') || lower.includes('curso') || lower.includes('livro') || lower.includes('faculdade') || lower.includes('escola') || lower.includes('inglês')) {
      category = 'Estudos';
    }

    const now = new Date();
    const targetDate = new Date();

    // 1. Check relative: "daqui a X minutos / horas"
    const relMinMatch = lower.match(/(?:daqui\s*a|em)\s*(\d+|meia|uma|duas|três|quatro|cinco|dez|quinze|vinte|trinta)\s*(?:minutos?|min)/i);
    if (relMinMatch) {
      const minStr = relMinMatch[1].toLowerCase();
      const mins = minStr === 'meia' ? 30 : minStr === 'uma' ? 1 : minStr === 'duas' ? 2 : minStr === 'três' ? 3 : minStr === 'quatro' ? 4 : minStr === 'cinco' ? 5 : minStr === 'dez' ? 10 : minStr === 'quinze' ? 15 : minStr === 'vinte' ? 20 : minStr === 'trinta' ? 30 : (parseInt(minStr, 10) || 10);
      targetDate.setMinutes(now.getMinutes() + mins);
      const cleanTitle = raw.replace(relMinMatch[0], '').replace(/^me\s+lembre\s+(?:de\s+)?/i, '').trim();
      return {
        title: cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1) || raw,
        category,
        timestamp: targetDate.getTime(),
        hour: targetDate.getHours(),
        minute: targetDate.getMinutes(),
        isTomorrow: false,
      };
    }

    // 2. Date determination
    const isTomorrow = lower.includes('amanhã') || lower.includes('amanha');
    const isDayAfter = lower.includes('depois de amanhã') || lower.includes('depois de amanha');
    if (isDayAfter) {
      targetDate.setDate(now.getDate() + 2);
    } else if (isTomorrow) {
      targetDate.setDate(now.getDate() + 1);
    }

    // 3. Normalization of spoken numbers
    let normalized = lower
      .replace(/meio-dia|meio dia/g, '12:00')
      .replace(/meia-noite|meia noite/g, '00:00')
      .replace(/uma hora/g, '1 hora')
      .replace(/duas horas/g, '2 horas')
      .replace(/três horas|tres horas/g, '3 horas')
      .replace(/quatro horas/g, '4 horas')
      .replace(/cinco horas/g, '5 horas')
      .replace(/seis horas/g, '6 horas')
      .replace(/sete horas/g, '7 horas')
      .replace(/oito horas/g, '8 horas')
      .replace(/nove horas/g, '9 horas')
      .replace(/dez horas/g, '10 horas')
      .replace(/onze horas/g, '11 horas')
      .replace(/doze horas/g, '12 horas')
      .replace(/(?:às|as)\s+uma\b/g, 'às 1')
      .replace(/(?:às|as)\s+duas\b/g, 'às 2')
      .replace(/(?:às|as)\s+três\b|(?:às|as)\s+tres\b/g, 'às 3')
      .replace(/(?:às|as)\s+quatro\b/g, 'às 4')
      .replace(/(?:às|as)\s+cinco\b/g, 'às 5')
      .replace(/(?:às|as)\s+seis\b/g, 'às 6')
      .replace(/(?:às|as)\s+sete\b/g, 'às 7')
      .replace(/(?:às|as)\s+oito\b/g, 'às 8')
      .replace(/(?:às|as)\s+nove\b/g, 'às 9')
      .replace(/(?:às|as)\s+dez\b/g, 'às 10')
      .replace(/(?:às|as)\s+onze\b/g, 'às 11')
      .replace(/e\s+meia\b/g, 'e 30')
      .replace(/e\s+quinze\b/g, 'e 15')
      .replace(/e\s+quarenta\s+e\s+cinco\b/g, 'e 45')
      .replace(/e\s+vinte\b/g, 'e 20');

    const isTarde = normalized.includes('tarde');
    const isNoite = normalized.includes('noite');
    const isManha = normalized.includes('manhã') || normalized.includes('manha') || normalized.includes('madrugada');

    // 4. Time matching
    // Digital: 14:30 or 14h30
    const digitalMatch = normalized.match(/\b(\d{1,2})[:hH](\d{2})\b/);
    // Horas: 14 horas e 30 minutos / 10 horas
    const horasMatch = normalized.match(/\b(\d{1,2})\s*(?:horas?|hrs?|hs?|h)\s*(?:(?:e|com)\s*(\d{1,2})(?:\s*minutos?)?)?\b/);
    // às HH e MM / às HH
    const asMatch = normalized.match(/(?:às|as|para\s+(?:às|as)?)\s*(\d{1,2})(?:\s*e\s*(\d{1,2}))?\b/);

    let hour: number | null = null;
    let minute = 0;
    let matchedPhrase = '';

    if (digitalMatch) {
      hour = parseInt(digitalMatch[1], 10);
      minute = parseInt(digitalMatch[2], 10) || 0;
      matchedPhrase = digitalMatch[0];
    } else if (horasMatch) {
      hour = parseInt(horasMatch[1], 10);
      minute = horasMatch[2] ? parseInt(horasMatch[2], 10) : 0;
      matchedPhrase = horasMatch[0];
    } else if (asMatch) {
      hour = parseInt(asMatch[1], 10);
      minute = asMatch[2] ? parseInt(asMatch[2], 10) : 0;
      matchedPhrase = asMatch[0];
    }

    if (hour !== null) {
      hour = Math.min(23, Math.max(0, hour));
      minute = Math.min(59, Math.max(0, minute));

      if ((isTarde || isNoite) && hour >= 1 && hour <= 11) {
        hour += 12;
      } else if (isManha && hour === 12) {
        hour = 0;
      } else if (!isTarde && !isNoite && !isManha && !isTomorrow && !isDayAfter) {
        const curHour = now.getHours();
        if (hour >= 1 && hour <= 11 && hour <= curHour && (hour + 12) > curHour) {
          hour += 12;
        }
      }

      targetDate.setHours(hour, minute, 0, 0);

      if (targetDate.getTime() <= now.getTime() && !isTomorrow && !isDayAfter) {
        targetDate.setDate(targetDate.getDate() + 1);
      }
    } else {
      targetDate.setHours(now.getHours() + 1, 0, 0, 0);
    }

    // Clean Title
    let clean = raw
      .replace(/^me\s+lembre\s+(?:de\s+|para\s+|que\s+)?/i, '')
      .replace(/^lembrete\s+(?:de\s+|para\s+)?/i, '')
      .replace(/^agendar\s+/i, '')
      .replace(/^marcar\s+/i, '')
      .replace(/^criar\s+lembrete\s+(?:de\s+|para\s+)?/i, '')
      .replace(/\bdepois\s+de\s+amanh[ãa]\b/gi, '')
      .replace(/\bamanh[ãa]\b/gi, '')
      .replace(/\bhoje\b/gi, '')
      .replace(/\bda\s+manh[ãa]\b/gi, '')
      .replace(/\bda\s+tarde\b/gi, '')
      .replace(/\bda\s+noite\b/gi, '');

    if (matchedPhrase) {
      clean = clean.replace(new RegExp(matchedPhrase, 'gi'), '');
    }

    clean = clean.replace(/\s+às\s*$/i, '').replace(/\s+as\s*$/i, '').replace(/\s+para\s*$/i, '').replace(/\s{2,}/g, ' ').trim();
    const finalTitle = clean.length > 0 ? (clean.charAt(0).toUpperCase() + clean.slice(1)) : raw;

    return {
      title: finalTitle,
      category,
      timestamp: targetDate.getTime(),
      hour: targetDate.getHours(),
      minute: targetDate.getMinutes(),
      isTomorrow: targetDate.getDate() !== now.getDate(),
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
        signUpWithEmail,
        signInWithEmail,
        signOutUser,
        addReminder,
        postponeReminder,
        toggleComplete,
        deleteReminder,
        isRinging,
        activeAlarmTitle,
        stopAlarm,
        loginAsDemo,
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

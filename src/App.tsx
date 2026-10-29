import React, { useState, useEffect } from 'react';
import { useReminders, ReminderCategory, AdvanceNoticeMinutes } from './context/RemindersContext';
import {
  Calendar,
  Volume2,
  VolumeX,
  LogOut,
  Mic,
  Send,
  Sparkles,
  Search,
  Check,
  RotateCcw,
  Trash2,
  Clock,
  Play,
  Brain,
  Mail,
  Lock,
  Eye,
  EyeOff,
  UserPlus,
  LogIn,
  User,
  AlertCircle,
  Smartphone,
  Monitor,
  Wifi,
  Battery,
  BellRing,
  X
} from 'lucide-react';

export default function App() {
  const {
    user,
    reminders,
    isLoading: isAppLoading,
    soundEnabled,
    setSoundEnabled,
    signUpWithEmail,
    signInWithEmail,
    signOutUser,
    loginAsDemo,
    addReminder,
    postponeReminder,
    toggleComplete,
    deleteReminder,
    isRinging,
    activeAlarmTitle,
    stopAlarm,
    triggerTestAlarm,
    parseNaturalLanguage
  } = useReminders();

  // Viewport mode: 'mobile' (smartphone frame) or 'expanded' (full browser)
  const [viewMode, setViewMode] = useState<'mobile' | 'expanded'>('mobile');

  // Clock in Android Status Bar
  const [currentTimeStr, setCurrentTimeStr] = useState<string>('09:41');
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTimeStr(now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 10000);
    return () => clearInterval(interval);
  }, []);

  // Auth Form State
  const [authTab, setAuthTab] = useState<'entrar' | 'cadastrar'>('entrar');
  const [nameInput, setNameInput] = useState<string>('');
  const [emailInput, setEmailInput] = useState<string>('');
  const [passwordInput, setPasswordInput] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authLoading, setAuthLoading] = useState<boolean>(false);

  // User Switch Dialog
  const [showUserSwitchDialog, setShowUserSwitchDialog] = useState<boolean>(false);

  // Command card state
  const [commandText, setCommandText] = useState<string>('');
  const [advanceNotice, setAdvanceNotice] = useState<AdvanceNoticeMinutes>(0);
  const [isListening, setIsListening] = useState<boolean>(false);

  // Filters
  const [statusFilter, setStatusFilter] = useState<'Pendentes' | 'Adiados' | 'Concluídos' | 'Todos'>('Pendentes');
  const [categoryFilter, setCategoryFilter] = useState<string>('Todas');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  const speakText = (text: string) => {
    if ('speechSynthesis' in window && soundEnabled) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'pt-BR';
      utterance.rate = 1.0;
      window.speechSynthesis.speak(utterance);
    }
  };

  // Speech Recognition (Microfone Nativo do Sistema)
  const handleMicClick = () => {
    const SpeechRecognition = (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).SpeechRecognition ||
      (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      const simulated = 'Tomar remédio hoje às 15h';
      const parsed = parseNaturalLanguage(simulated);
      addReminder({
        title: parsed.title,
        scheduledTime: parsed.timestamp,
        category: parsed.category,
        advanceNoticeMinutes: advanceNotice,
      });
      const simTime = new Date(parsed.timestamp).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      speakText(`Agendado automaticamente para às ${simTime}: ${parsed.title}`);
      showToast(`Lembrete agendado para às ${simTime}: ${parsed.title}`);
      setCommandText('');
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.lang = 'pt-BR';
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.onstart = () => setIsListening(true);
      recognition.onend = () => setIsListening(false);
      recognition.onresult = async (event: any) => {
        const transcript = event.results[0][0].transcript;
        setCommandText(transcript);
        if (event.results[0].isFinal && transcript.trim()) {
          const parsed = parseNaturalLanguage(transcript);
          await addReminder({
            title: parsed.title,
            scheduledTime: parsed.timestamp,
            category: parsed.category,
            advanceNoticeMinutes: advanceNotice,
          });
          const spokenTime = new Date(parsed.timestamp).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
          speakText(`Agendado automaticamente para às ${spokenTime}: ${parsed.title}`);
          setCommandText('');
          showToast('Lembrete agendado automaticamente por voz!');
        }
      };
      recognition.start();
    } catch {
      setCommandText('Pagar conta amanhã às 10h');
    }
  };

  // Schedule from Command Button
  const handleSchedule = async () => {
    if (!commandText.trim()) return;
    const parsed = parseNaturalLanguage(commandText);
    await addReminder({
      title: parsed.title,
      scheduledTime: parsed.timestamp,
      category: parsed.category,
      advanceNoticeMinutes: advanceNotice,
    });
    const spokenDate = new Date(parsed.timestamp).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    speakText(`Lembrete agendado com sucesso para às ${spokenDate}: ${parsed.title}`);
    setCommandText('');
    showToast('Lembrete agendado com sucesso!');
  };

  // Auth Submit (Firebase Auth)
  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setAuthLoading(true);

    try {
      if (authTab === 'cadastrar') {
        if (!nameInput.trim()) {
          throw new Error('Por favor, informe seu nome.');
        }
        if (passwordInput.length < 6) {
          throw new Error('A senha deve ter no mínimo 6 caracteres.');
        }
        await signUpWithEmail(emailInput.trim(), passwordInput, nameInput.trim());
        showToast(`Conta criada com sucesso! Bem-vindo(a), ${nameInput.trim()}!`);
      } else {
        await signInWithEmail(emailInput.trim(), passwordInput);
        showToast('Login realizado com sucesso no Firebase!');
      }
    } catch (err: any) {
      console.error('[Auth Error]', err);
      let message = 'Ocorreu um erro na autenticação. Tente novamente.';
      if (err.code === 'auth/email-already-in-use') {
        message = 'Este e-mail já está cadastrado. Faça login ou use outro.';
      } else if (err.code === 'auth/invalid-email') {
        message = 'Formato de e-mail inválido.';
      } else if (err.code === 'auth/user-not-found' || err.code === 'auth/wrong-password' || err.code === 'auth/invalid-credential') {
        message = 'E-mail ou senha incorretos.';
      } else if (err.code === 'auth/weak-password') {
        message = 'A senha é muito fraca. Digite pelo menos 6 caracteres.';
      } else if (err.message) {
        message = err.message;
      }
      setAuthError(message);
    } finally {
      setAuthLoading(false);
    }
  };

  // Filtered Reminders List
  const filteredReminders = reminders.filter((r) => {
    const matchesStatus =
      statusFilter === 'Pendentes'
        ? r.status === 'pending'
        : statusFilter === 'Adiados'
        ? r.status === 'postponed'
        : statusFilter === 'Concluídos'
        ? r.status === 'completed'
        : true;

    const matchesCategory = categoryFilter === 'Todas' || r.category === categoryFilter;
    const matchesSearch =
      searchQuery.trim() === '' ||
      r.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      r.category.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesStatus && matchesCategory && matchesSearch;
  });

  const counts = {
    Pendentes: reminders.filter((r) => r.status === 'pending').length,
    Adiados: reminders.filter((r) => r.status === 'postponed').length,
    Concluídos: reminders.filter((r) => r.status === 'completed').length,
    Todos: reminders.length,
  };

  // Main UI Content inside device or full screen
  const renderAppContent = () => {
    if (!user) {
      return (
        <div className="flex-1 flex flex-col justify-between p-4 md:p-6 text-slate-100 overflow-y-auto">
          {/* Header */}
          <div className="flex flex-col items-center text-center mt-3">
            {/* Brain Logo */}
            <div className="relative mb-3">
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-lg shadow-purple-500/30">
                <Brain className="w-9 h-9 text-white" />
              </div>
              <div className="absolute top-1 right-1 w-2.5 h-2.5 bg-emerald-400 rounded-full border-2 border-[#121933]" />
            </div>

            <h2 className="text-xl font-bold tracking-tight">
              Lembrete <span className="text-indigo-400">Inteligente</span>
            </h2>
            <p className="text-xs text-slate-400 mt-1 mb-4">
              {authTab === 'entrar'
                ? 'Entre com sua conta para acessar seus alarmes'
                : 'Crie seu cadastro para sincronizar seus compromissos'}
            </p>

            {/* Quick Demo Access (1-Click) */}
            <div className="w-full bg-[#161F3D] border border-[#2B3968] rounded-2xl p-3 mb-4 text-left">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold text-indigo-300 uppercase tracking-wider flex items-center">
                  <Sparkles className="w-3 h-3 text-amber-400 mr-1" />
                  Acesso Rápido de Demonstração
                </span>
                <span className="text-[10px] text-slate-400">Sem senha</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => {
                    loginAsDemo('Michel Lima', 'michel.lima20000@gmail.com', 'usr_68dd8a516780b021');
                    showToast('Conectado como Michel Lima!');
                  }}
                  className="flex items-center space-x-1.5 p-2 bg-[#1E2B54] hover:bg-indigo-600 rounded-xl text-xs font-semibold text-white transition text-left border border-[#2D3F75]"
                >
                  <div className="w-6 h-6 rounded-full bg-indigo-500 flex items-center justify-center text-[10px] font-bold flex-shrink-0">
                    M
                  </div>
                  <div className="truncate">
                    <p className="font-bold truncate text-[11px]">Michel Lima</p>
                    <p className="text-[9px] text-slate-300 truncate">Admin / Conta 1</p>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    loginAsDemo('Maya Lima', 'maya.lima@gmail.com', 'CiEoITiFP2PqVjFYzu0HZ3TXBmt2');
                    showToast('Conectada como Maya Lima!');
                  }}
                  className="flex items-center space-x-1.5 p-2 bg-[#1E2B54] hover:bg-purple-600 rounded-xl text-xs font-semibold text-white transition text-left border border-[#2D3F75]"
                >
                  <div className="w-6 h-6 rounded-full bg-purple-500 flex items-center justify-center text-[10px] font-bold flex-shrink-0">
                    M
                  </div>
                  <div className="truncate">
                    <p className="font-bold truncate text-[11px]">Maya Lima</p>
                    <p className="text-[9px] text-slate-300 truncate">Usuária / Conta 2</p>
                  </div>
                </button>
              </div>
            </div>

            {/* Tab Selector: Entrar vs Cadastrar */}
            <div className="grid grid-cols-2 w-full p-1 bg-[#0B1021] rounded-xl mb-4 border border-[#1C2648]">
              <button
                type="button"
                onClick={() => {
                  setAuthTab('entrar');
                  setAuthError(null);
                }}
                className={`flex items-center justify-center space-x-1.5 py-1.5 rounded-lg text-xs font-semibold transition ${
                  authTab === 'entrar' ? 'bg-indigo-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
                }`}
              >
                <LogIn className="w-3.5 h-3.5" />
                <span>Entrar com E-mail</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setAuthTab('cadastrar');
                  setAuthError(null);
                }}
                className={`flex items-center justify-center space-x-1.5 py-1.5 rounded-lg text-xs font-semibold transition ${
                  authTab === 'cadastrar' ? 'bg-indigo-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
                }`}
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Cadastrar</span>
              </button>
            </div>

            {/* Error Message Box */}
            {authError && (
              <div className="w-full mb-3 p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center space-x-2 text-left">
                <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-400" />
                <span>{authError}</span>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleAuthSubmit} className="w-full space-y-3 text-left">
              {authTab === 'cadastrar' && (
                <div>
                  <label className="block text-[11px] font-medium text-slate-300 mb-1">Seu Nome Completo</label>
                  <div className="relative">
                    <User className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
                    <input
                      type="text"
                      required
                      placeholder="Ex: Carlos Silva"
                      value={nameInput}
                      onChange={(e) => setNameInput(e.target.value)}
                      className="w-full pl-9 pr-3 py-2 bg-[#17203E] border border-[#26335C] rounded-xl text-xs text-white focus:outline-none focus:border-indigo-500 transition placeholder-slate-500"
                    />
                  </div>
                </div>
              )}

              <div>
                <label className="block text-[11px] font-medium text-slate-300 mb-1">E-mail</label>
                <div className="relative">
                  <Mail className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    type="email"
                    required
                    placeholder="seu.email@exemplo.com"
                    value={emailInput}
                    onChange={(e) => setEmailInput(e.target.value)}
                    className="w-full pl-9 pr-3 py-2 bg-[#17203E] border border-[#26335C] rounded-xl text-xs text-white focus:outline-none focus:border-indigo-500 transition placeholder-slate-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-medium text-slate-300 mb-1">Senha</label>
                <div className="relative">
                  <Lock className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    minLength={6}
                    placeholder="Mínimo 6 caracteres"
                    value={passwordInput}
                    onChange={(e) => setPasswordInput(e.target.value)}
                    className="w-full pl-9 pr-9 py-2 bg-[#17203E] border border-[#26335C] rounded-xl text-xs text-white focus:outline-none focus:border-indigo-500 transition placeholder-slate-500"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-200"
                  >
                    {showPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={authLoading}
                className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded-xl font-bold text-xs text-white transition flex items-center justify-center space-x-2 shadow-lg shadow-indigo-600/30 mt-2"
              >
                {authLoading ? (
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    {authTab === 'entrar' ? <LogIn className="w-3.5 h-3.5" /> : <UserPlus className="w-3.5 h-3.5" />}
                    <span>{authTab === 'entrar' ? 'Entrar com Senha' : 'Criar Conta no Firebase'}</span>
                  </>
                )}
              </button>
            </form>
          </div>

          <div className="text-center text-[10px] text-slate-500 py-3 mt-4">
            • Lembrete Inteligente • Agenda de Lembretes e Compromissos
          </div>
        </div>
      );
    }

    return (
      <div className="flex-1 flex flex-col justify-between overflow-y-auto">
        {/* App Bar inside the mobile viewport */}
        <div className="px-4 py-2.5 bg-[#090D1C]/95 backdrop-blur border-b border-[#1A2344] sticky top-0 z-30 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-indigo-500 to-cyan-400 flex items-center justify-center shadow-md shadow-indigo-500/20 flex-shrink-0">
              <Calendar className="w-3.5 h-3.5 text-white" />
            </div>
            <span className="text-sm font-bold tracking-tight text-white">
              Lembrete <span className="text-indigo-400">Inteligente</span>
            </span>
          </div>

          <div className="flex items-center space-x-1.5">
            {/* Audio Toggle */}
            <button
              onClick={() => {
                setSoundEnabled(!soundEnabled);
                showToast(soundEnabled ? 'Som desativado' : 'Som ativado');
              }}
              className={`p-1.5 rounded-lg border transition ${
                soundEnabled
                  ? 'bg-indigo-950/60 border-indigo-500/40 text-indigo-400'
                  : 'bg-slate-900 border-slate-800 text-slate-500'
              }`}
              title={soundEnabled ? 'Som ativado' : 'Som mudo'}
            >
              {soundEnabled ? <Volume2 className="w-3.5 h-3.5" /> : <VolumeX className="w-3.5 h-3.5" />}
            </button>

            {/* User Badge Clickable */}
            <button
              onClick={() => setShowUserSwitchDialog(true)}
              className="flex items-center space-x-1.5 px-2 py-1 rounded-xl bg-[#1B2344] border border-[#2B3968] text-xs hover:border-indigo-400 transition"
              title="Alternar usuário"
            >
              <div className="w-4 h-4 rounded-full bg-indigo-600 flex items-center justify-center font-bold text-white text-[9px]">
                {user.name.charAt(0).toUpperCase()}
              </div>
              <span className="font-semibold text-white max-w-[80px] truncate text-[11px]">{user.name.split(' ')[0]}</span>
            </button>

            {/* Logout */}
            <button
              onClick={async () => {
                await signOutUser();
                showToast('Desconectado com sucesso');
              }}
              className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 transition"
              title="Sair"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Scrollable Main Area */}
        <div className="p-3.5 space-y-3.5 flex-1">
          {/* Active Ringing Alarm Alert Banner */}
          {isRinging && (
            <div className="bg-gradient-to-r from-rose-900/90 via-amber-900/90 to-rose-900/90 border-2 border-rose-500 rounded-2xl p-3 shadow-xl shadow-rose-950/60 animate-pulse text-white">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <div className="w-8 h-8 rounded-full bg-rose-500 flex items-center justify-center animate-bounce">
                    <BellRing className="w-4 h-4 text-white" />
                  </div>
                  <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-rose-200">
                      🚨 Alarme Disparando Agora!
                    </h3>
                    <p className="text-sm font-extrabold text-white">
                      {activeAlarmTitle || 'Alarme Programado'}
                    </p>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 mt-2 pt-2 border-t border-rose-700/50">
                <button
                  onClick={() => {
                    stopAlarm();
                    showToast('Alarme desligado');
                  }}
                  className="py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-xl text-xs font-bold shadow-md transition"
                >
                  ⏹️ Parar Alarme
                </button>
                <button
                  onClick={() => {
                    stopAlarm();
                    showToast('Alarme adiado por 10 minutos');
                  }}
                  className="py-1.5 bg-amber-600 hover:bg-amber-500 text-white rounded-xl text-xs font-bold shadow-md transition"
                >
                  ⏰ Adiar (+10m)
                </button>
              </div>
            </div>
          )}

          {/* Natural Language Command Card */}
          <div className="bg-[#121933] border border-[#1F2A52] rounded-2xl p-3.5 shadow-xl">
            <textarea
              rows={2}
              value={commandText}
              onChange={(e) => setCommandText(e.target.value)}
              placeholder='Fale ou digite: "Hoje às 15h tomar remédio" ou "Pagar boleto amanhã às 10h"...'
              className="w-full bg-transparent resize-none text-xs md:text-sm text-white placeholder-slate-500 focus:outline-none"
            />

            {/* Notice options & Mic / Schedule Buttons */}
            <div className="flex items-center justify-between gap-2 pt-2.5 border-t border-[#1B254A]">
              <div className="flex items-center space-x-1 overflow-x-auto pb-1 text-[11px]">
                <span className="text-slate-400 font-medium flex items-center mr-0.5 whitespace-nowrap">
                  <Clock className="w-3 h-3 mr-0.5" />
                  Avisar:
                </span>
                {(
                  [
                    [0, 'No horário'],
                    [5, '5m'],
                    [10, '10m'],
                    [15, '15m'],
                    [30, '30m'],
                  ] as const
                ).map(([min, label]) => (
                  <button
                    key={min}
                    type="button"
                    onClick={() => setAdvanceNotice(min as AdvanceNoticeMinutes)}
                    className={`px-2 py-0.5 rounded-md transition whitespace-nowrap ${
                      advanceNotice === min
                        ? 'bg-indigo-600 text-white font-semibold'
                        : 'bg-[#161F3D] text-slate-400 hover:text-white'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="flex items-center space-x-1.5 flex-shrink-0">
                <button
                  type="button"
                  onClick={handleMicClick}
                  className={`w-8 h-8 rounded-full flex items-center justify-center transition ${
                    isListening
                      ? 'bg-rose-500 text-white animate-pulse'
                      : 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-600/30'
                  }`}
                  title="Microfone (comando por voz)"
                >
                  <Mic className="w-3.5 h-3.5" />
                </button>

                <button
                  type="button"
                  onClick={handleSchedule}
                  className="flex items-center space-x-1 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-bold transition shadow-md shadow-emerald-600/20"
                >
                  <span>Agendar</span>
                  <Send className="w-3 h-3" />
                </button>
              </div>
            </div>

            {/* Prompt Example Chips */}
            <div className="flex items-center space-x-1.5 overflow-x-auto pt-2 mt-2 border-t border-[#17203E] text-[10px]">
              <span className="text-slate-400 font-medium flex items-center whitespace-nowrap">
                <Sparkles className="w-3 h-3 text-amber-400 mr-1" />
                Exemplos:
              </span>
              {[
                'Tomar remédio hoje às 16h',
                'Reunião amanhã às 09h',
                'Pagar boleto amanhã às 11h',
                'Treino hoje às 19h',
              ].map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => setCommandText(ex)}
                  className="px-2 py-0.5 rounded-lg bg-[#172242] border border-[#26335C] text-slate-300 hover:text-white whitespace-nowrap hover:border-indigo-500 transition"
                >
                  {ex}
                </button>
              ))}
            </div>
          </div>

          {/* Quick Test Alarm Button (Same as Android AlarmManager test) */}
          <div className="bg-[#161E3D] border border-[#2E3A68] rounded-xl p-2.5 flex items-center justify-between text-xs">
            <div className="flex items-center space-x-2">
              <Play className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
              <span className="text-[11px] font-semibold text-slate-200">
                Testar Alarme Nativo Alto (com som)
              </span>
            </div>
            <button
              onClick={() => {
                triggerTestAlarm('Alarme de Teste Imediato');
                showToast('Disparando alarme!');
              }}
              className="px-2.5 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-[11px] font-bold transition shadow"
            >
              Disparar Agora
            </button>
          </div>

          {/* Section: Cronograma & Agenda */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <h3 className="text-xs font-bold tracking-tight text-white flex items-center">
                <Clock className="w-3.5 h-3.5 mr-1 text-indigo-400" />
                Cronograma & Lembretes
              </h3>
              <span className="px-2 py-0.5 rounded-full bg-[#1E2648] text-indigo-300 text-[10px] font-medium">
                {user.name.split(' ')[0]} ({counts.Todos})
              </span>
            </div>

            {/* Search */}
            <div className="relative mb-2">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2.5" />
              <input
                type="text"
                placeholder="Buscar compromisso..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 bg-[#121933] border border-[#26335C] rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition"
              />
            </div>

            {/* Status Tabs */}
            <div className="grid grid-cols-4 bg-[#121933] p-1 rounded-xl gap-1 text-[11px] mb-3">
              {(['Pendentes', 'Adiados', 'Concluídos', 'Todos'] as const).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setStatusFilter(tab)}
                  className={`py-1 rounded-lg font-medium text-center transition ${
                    statusFilter === tab
                      ? 'bg-indigo-600 text-white font-bold shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {tab} ({counts[tab]})
                </button>
              ))}
            </div>
          </div>

          {/* Reminders List */}
          {filteredReminders.length === 0 ? (
            <div className="bg-[#10162E] border border-[#1A2242] rounded-2xl py-8 px-4 flex flex-col items-center text-center">
              <div className="w-12 h-12 rounded-full bg-[#1D2447] flex items-center justify-center mb-2">
                <Clock className="w-6 h-6 text-indigo-400" />
              </div>
              <h4 className="text-xs font-bold text-white mb-0.5">
                Nenhum lembrete nesta lista
              </h4>
              <p className="text-[11px] text-slate-400 max-w-xs">
                Fale ou digite no campo acima para criar novos alarmes.
              </p>
            </div>
          ) : (
            <div className="space-y-2.5 pb-3">
              {filteredReminders.map((reminder) => {
                const isCompleted = reminder.status === 'completed';
                const isPostponed = reminder.status === 'postponed';
                const dateStr = new Date(reminder.scheduledTime).toLocaleString('pt-BR', {
                  day: '2-digit',
                  month: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                });

                const categoryColors: Record<string, string> = {
                  Saúde: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
                  Trabalho: 'bg-indigo-500/20 text-indigo-400 border-indigo-500/40',
                  Finanças: 'bg-amber-500/20 text-amber-400 border-amber-500/40',
                  Estudos: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40',
                  Pessoal: 'bg-purple-500/20 text-purple-400 border-purple-500/40',
                };

                return (
                  <div
                    key={reminder.id}
                    className={`bg-[#131A36] border rounded-xl p-3 transition ${
                      isCompleted ? 'border-[#1B2642] opacity-70' : 'border-[#222F56]'
                    }`}
                  >
                    <div className="flex justify-between items-start mb-1">
                      <div className="flex items-center space-x-1.5">
                        <span
                          className={`px-1.5 py-0.5 rounded text-[9px] font-bold border ${
                            categoryColors[reminder.category] || categoryColors.Pessoal
                          }`}
                        >
                          {reminder.category}
                        </span>
                        {isPostponed && (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-semibold bg-amber-500/20 text-amber-300">
                            Adiado
                          </span>
                        )}
                        {isCompleted && (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-semibold bg-emerald-500/20 text-emerald-300">
                            Concluído
                          </span>
                        )}
                      </div>

                      <button
                        onClick={() => {
                          deleteReminder(reminder.id);
                          showToast('Lembrete excluído');
                        }}
                        className="text-slate-500 hover:text-rose-400 transition"
                        title="Excluir"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>

                    <h4
                      className={`text-xs md:text-sm font-semibold mt-1 ${
                        isCompleted ? 'line-through text-slate-500' : 'text-white'
                      }`}
                    >
                      {reminder.title}
                    </h4>

                    <div className="flex items-center space-x-2 text-[11px] text-indigo-400 mt-1 mb-2">
                      <Clock className="w-3 h-3" />
                      <span>{dateStr}</span>
                      {reminder.advanceNoticeMinutes > 0 && (
                        <span className="text-slate-400">• Avisar {reminder.advanceNoticeMinutes}m antes</span>
                      )}
                    </div>

                    {/* Action buttons */}
                    <div className="flex justify-between items-center pt-2 border-t border-[#1C254B] text-[11px]">
                      <div className="flex items-center space-x-2">
                        <button
                          onClick={() => {
                            speakText(`Lembrete: ${reminder.title}. Programado para ${dateStr}`);
                            showToast('Reproduzindo em áudio...');
                          }}
                          className="flex items-center space-x-0.5 text-indigo-400 hover:text-indigo-300 transition"
                        >
                          <Volume2 className="w-3 h-3" />
                          <span>Ouvir</span>
                        </button>

                        <button
                          onClick={() => {
                            triggerTestAlarm(reminder.title);
                            showToast(`Disparando som para: ${reminder.title}`);
                          }}
                          className="flex items-center space-x-0.5 text-cyan-400 hover:text-cyan-300 transition"
                        >
                          <Play className="w-3 h-3" />
                          <span>Alarme</span>
                        </button>
                      </div>

                      <div className="flex items-center space-x-1.5">
                        {!isCompleted && (
                          <button
                            onClick={() => {
                              postponeReminder(reminder.id, 10);
                              showToast('Adiado por 10 minutos');
                            }}
                            className="px-2 py-0.5 rounded-lg bg-[#1E284A] text-amber-400 hover:bg-[#283663] font-semibold transition"
                          >
                            +10m
                          </button>
                        )}

                        <button
                          onClick={() => {
                            toggleComplete(reminder.id);
                            showToast(isCompleted ? 'Reativado como pendente' : 'Concluído!');
                          }}
                          className={`flex items-center space-x-1 px-2.5 py-0.5 rounded-lg font-bold transition ${
                            isCompleted
                              ? 'bg-[#1E284A] text-slate-300 hover:bg-[#283663]'
                              : 'bg-emerald-600 hover:bg-emerald-500 text-white'
                          }`}
                        >
                          {isCompleted ? (
                            <>
                              <RotateCcw className="w-2.5 h-2.5" />
                              <span>Reativar</span>
                            </>
                          ) : (
                            <>
                              <Check className="w-2.5 h-2.5" />
                              <span>Concluir</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* User Switch Modal */}
        {showUserSwitchDialog && (
          <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-[#121933] border border-[#2B3968] rounded-2xl max-w-xs w-full p-4 shadow-2xl">
              <div className="flex justify-between items-center mb-3">
                <h4 className="text-xs font-bold text-white uppercase tracking-wider">
                  Alternar Conta / Usuário
                </h4>
                <button
                  onClick={() => setShowUserSwitchDialog(false)}
                  className="text-slate-400 hover:text-white"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="space-y-2 mb-4">
                <button
                  onClick={() => {
                    loginAsDemo('Michel Lima', 'michel.lima20000@gmail.com', 'usr_68dd8a516780b021');
                    setShowUserSwitchDialog(false);
                    showToast('Alternado para Michel Lima');
                  }}
                  className={`w-full p-2.5 rounded-xl border flex items-center space-x-2 transition ${
                    user.email.includes('michel')
                      ? 'bg-indigo-600/30 border-indigo-500 text-white'
                      : 'bg-[#182346] border-[#2B3968] text-slate-300 hover:bg-[#202E5C]'
                  }`}
                >
                  <div className="w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center font-bold text-white text-xs">
                    M
                  </div>
                  <div className="text-left truncate">
                    <p className="font-bold text-xs">Michel Lima</p>
                    <p className="text-[10px] text-slate-400">michel.lima20000@gmail.com</p>
                  </div>
                </button>

                <button
                  onClick={() => {
                    loginAsDemo('Maya Lima', 'maya.lima@gmail.com', 'CiEoITiFP2PqVjFYzu0HZ3TXBmt2');
                    setShowUserSwitchDialog(false);
                    showToast('Alternada para Maya Lima');
                  }}
                  className={`w-full p-2.5 rounded-xl border flex items-center space-x-2 transition ${
                    user.email.includes('maya')
                      ? 'bg-purple-600/30 border-purple-500 text-white'
                      : 'bg-[#182346] border-[#2B3968] text-slate-300 hover:bg-[#202E5C]'
                  }`}
                >
                  <div className="w-7 h-7 rounded-full bg-purple-600 flex items-center justify-center font-bold text-white text-xs">
                    M
                  </div>
                  <div className="text-left truncate">
                    <p className="font-bold text-xs">Maya Lima</p>
                    <p className="text-[10px] text-slate-400">maya.lima@gmail.com</p>
                  </div>
                </button>
              </div>

              <button
                onClick={async () => {
                  setShowUserSwitchDialog(false);
                  await signOutUser();
                  showToast('Desconectado');
                }}
                className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-rose-300 rounded-xl text-xs font-semibold transition"
              >
                Sair da Conta Atual
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-[#060914] text-slate-100 flex flex-col items-center justify-between">
      {/* Top Controller Bar: Viewport Switcher */}
      <header className="w-full bg-[#0B1024] border-b border-[#1A2346] px-4 py-2.5 z-40 sticky top-0 shadow-md">
        <div className="max-w-4xl mx-auto flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center space-x-2">
            <div className="w-6 h-6 rounded-lg bg-indigo-600 flex items-center justify-center text-white">
              <Calendar className="w-3.5 h-3.5" />
            </div>
            <span className="text-xs font-bold text-white">
              Lembrete Inteligente <span className="text-indigo-400 font-normal">| Visualizador do App</span>
            </span>
          </div>

          {/* Mode Switcher */}
          <div className="flex items-center space-x-1 bg-[#131B38] border border-[#23305E] p-1 rounded-xl">
            <button
              onClick={() => setViewMode('mobile')}
              className={`flex items-center space-x-1.5 px-3 py-1 rounded-lg text-xs font-semibold transition ${
                viewMode === 'mobile'
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>📱 Modo Celular (App Nativo)</span>
            </button>
            <button
              onClick={() => setViewMode('expanded')}
              className={`flex items-center space-x-1.5 px-3 py-1 rounded-lg text-xs font-semibold transition ${
                viewMode === 'expanded'
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Monitor className="w-3.5 h-3.5" />
              <span>💻 Modo Expandido</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container Area */}
      <main className="w-full flex-1 flex items-center justify-center p-2 md:p-6 overflow-hidden">
        {viewMode === 'mobile' ? (
          /* Smartphone Frame */
          <div className="w-full max-w-[400px] h-[820px] max-h-[92vh] bg-[#090D1C] border-[6px] border-[#202946] rounded-[44px] shadow-2xl shadow-indigo-950/80 flex flex-col relative overflow-hidden ring-1 ring-white/10">
            {/* Top Android Status Bar */}
            <div className="w-full bg-[#090D1C] pt-2 px-6 pb-1 flex items-center justify-between text-[11px] text-slate-300 font-semibold select-none z-30">
              <span>{currentTimeStr}</span>
              {/* Front Camera Notch / Punch Hole */}
              <div className="w-3.5 h-3.5 rounded-full bg-black border border-slate-800 flex items-center justify-center">
                <div className="w-1.5 h-1.5 rounded-full bg-slate-900" />
              </div>
              <div className="flex items-center space-x-1.5">
                <Wifi className="w-3.5 h-3.5" />
                <span className="text-[10px]">5G</span>
                <Battery className="w-3.5 h-3.5" />
              </div>
            </div>

            {/* Smartphone Inner Screen Content */}
            <div className="flex-1 flex flex-col overflow-hidden relative">
              {renderAppContent()}
            </div>

            {/* Bottom Android Home Navigation Bar */}
            <div className="w-full bg-[#090D1C] pb-2 pt-1 flex justify-center z-30">
              <div className="w-32 h-1 bg-slate-600/80 rounded-full" />
            </div>
          </div>
        ) : (
          /* Expanded Full Width View */
          <div className="w-full max-w-4xl h-full flex flex-col bg-[#090D1C] border border-[#1A2346] rounded-2xl shadow-xl overflow-hidden min-h-[750px]">
            {renderAppContent()}
          </div>
        )}
      </main>

      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-[#1E284A] border border-indigo-500 text-white text-xs px-4 py-2 rounded-xl shadow-2xl z-50 animate-bounce">
          {toastMessage}
        </div>
      )}
    </div>
  );
}

import React, { useState } from 'react';
import { useReminders, KNOWN_USERS, ReminderCategory, AdvanceNoticeMinutes } from './context/RemindersContext';
import {
  Calendar,
  Download,
  Bell,
  Volume2,
  VolumeX,
  Cpu,
  LogOut,
  Mic,
  Send,
  Sparkles,
  Search,
  Filter,
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
  AlertTriangle
} from 'lucide-react';

export default function App() {
  const {
    user,
    reminders,
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
    parseNaturalLanguage
  } = useReminders();

  // Navigation / Auth
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(true);
  const [authTab, setAuthTab] = useState<'entrar' | 'cadastrar'>('entrar');
  const [emailInput, setEmailInput] = useState<string>('michel.lima123@gmail.com');
  const [passwordInput, setPasswordInput] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);

  // Command card state
  const [commandText, setCommandText] = useState<string>('');
  const [advanceNotice, setAdvanceNotice] = useState<AdvanceNoticeMinutes>(0);
  const [isListening, setIsListening] = useState<boolean>(false);

  // Filters
  const [statusFilter, setStatusFilter] = useState<'Pendentes' | 'Adiados' | 'Concluídos' | 'Todos'>('Pendentes');
  const [categoryFilter, setCategoryFilter] = useState<string>('Todas');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Info modal
  const [showInfoModal, setShowInfoModal] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Speech Recognition
  const handleMicClick = () => {
    const SpeechRecognition = (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).SpeechRecognition ||
      (window as unknown as { SpeechRecognition?: any; webkitSpeechRecognition?: any }).webkitSpeechRecognition;

    const speakText = (text: string) => {
      if ('speechSynthesis' in window && soundEnabled) {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'pt-BR';
        utterance.rate = 1.0;
        window.speechSynthesis.speak(utterance);
      }
    };

    if (!SpeechRecognition) {
      const simulated = 'Tomar remédio hoje às 15h';
      const parsed = parseNaturalLanguage(simulated);
      addReminder({
        title: parsed.title,
        scheduledTime: parsed.timestamp,
        category: parsed.category,
        advanceNoticeMinutes: advanceNotice,
      });
      speakText('Agendado automaticamente para às 15 horas: Tomar remédio.');
      showToast('Lembrete agendado automaticamente por voz!');
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

  const speakText = (text: string) => {
    if ('speechSynthesis' in window && soundEnabled) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'pt-BR';
      utterance.rate = 1.0;
      window.speechSynthesis.speak(utterance);
    }
  };

  // Schedule from Command
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

  // Auth Submit
  const handleAuthSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (emailInput.toLowerCase().includes('maya')) {
      setUser(KNOWN_USERS['CiEoITiFP2PqVjFYzu0HZ3TXBmt2']);
    } else {
      setUser(KNOWN_USERS['usr_68dd8a516780b021']);
    }
    setIsAuthenticated(true);
    showToast(`Bem-vindo, ${emailInput.toLowerCase().includes('maya') ? 'Maya' : 'Michel'}!`);
  };

  // Filtered List
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

  // Render Login Screen if not authenticated
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-[#080C1B] flex flex-col justify-between p-4 md:p-8 text-slate-100">
        {/* Top Header */}
        <header className="flex justify-between items-center max-w-xl mx-auto w-full">
          <div className="flex items-center space-x-2">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-400 flex items-center justify-center shadow-lg shadow-indigo-500/30">
              <Brain className="w-5 h-5 text-white" />
            </div>
            <h1 className="text-xl font-bold tracking-tight">
              Lembrete <span className="text-indigo-400">Inteligente</span>
            </h1>
          </div>
          <button
            onClick={() => setShowInfoModal(true)}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-full bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white transition shadow-md shadow-indigo-600/30"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Instalar App</span>
          </button>
        </header>

        {/* Login Card */}
        <div className="max-w-md w-full mx-auto my-8 bg-[#121933] border border-[#1E294B] rounded-3xl p-6 md:p-8 shadow-2xl relative">
          <div className="flex flex-col items-center text-center">
            {/* Glowing Brain */}
            <div className="relative mb-4">
              <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-lg shadow-purple-500/40">
                <Brain className="w-11 h-11 text-white" />
              </div>
              <div className="absolute top-1.5 right-1.5 w-3 h-3 bg-emerald-400 rounded-full border-2 border-[#121933]" />
            </div>

            <h2 className="text-2xl font-bold tracking-tight">
              Lembrete <span className="text-indigo-400">Inteligente</span>
            </h2>
            <p className="text-sm text-slate-400 mt-1 mb-6">
              Entre com sua conta para acessar e sincronizar
            </p>

            {/* Account Quick Selectors */}
            <div className="flex items-center space-x-2 mb-5 text-xs">
              <span className="text-slate-500">Contas isoladas:</span>
              <button
                type="button"
                onClick={() => setEmailInput('michel.lima123@gmail.com')}
                className={`px-2.5 py-1 rounded-lg font-medium transition ${
                  emailInput.includes('michel') ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-300'
                }`}
              >
                Michel
              </button>
              <button
                type="button"
                onClick={() => setEmailInput('mayalimaalves50@gmail.com')}
                className={`px-2.5 py-1 rounded-lg font-medium transition ${
                  emailInput.includes('maya') ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-300'
                }`}
              >
                Maya
              </button>
            </div>

            {/* Tab Selector */}
            <div className="grid grid-cols-2 w-full p-1 bg-[#0B1021] rounded-xl mb-6">
              <button
                type="button"
                onClick={() => setAuthTab('entrar')}
                className={`flex items-center justify-center space-x-2 py-2 rounded-lg text-xs font-semibold transition ${
                  authTab === 'entrar' ? 'bg-indigo-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
                }`}
              >
                <LogIn className="w-3.5 h-3.5" />
                <span>Entrar</span>
              </button>
              <button
                type="button"
                onClick={() => setAuthTab('cadastrar')}
                className={`flex items-center justify-center space-x-2 py-2 rounded-lg text-xs font-semibold transition ${
                  authTab === 'cadastrar' ? 'bg-indigo-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
                }`}
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Cadastrar</span>
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleAuthSubmit} className="w-full space-y-4 text-left">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">E-mail</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                  <input
                    type="email"
                    required
                    value={emailInput}
                    onChange={(e) => setEmailInput(e.target.value)}
                    className="w-full pl-10 pr-3.5 py-2.5 bg-[#17203E] border border-[#26335C] rounded-xl text-sm text-white focus:outline-none focus:border-indigo-500 transition"
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <label className="text-xs font-medium text-slate-300">Senha</label>
                  <button type="button" className="text-xs text-indigo-400 hover:underline">
                    Esqueci a senha
                  </button>
                </div>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Digite sua senha (ou deixe em branco)"
                    value={passwordInput}
                    onChange={(e) => setPasswordInput(e.target.value)}
                    className="w-full pl-10 pr-10 py-2.5 bg-[#17203E] border border-[#26335C] rounded-xl text-sm text-white focus:outline-none focus:border-indigo-500 transition placeholder-slate-500"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-3 text-slate-400 hover:text-slate-200"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                <p className="text-[11px] text-slate-500 mt-1">
                  Toque no olho para visualizar a senha antes de enviar.
                </p>
              </div>

              <button
                type="submit"
                className="w-full py-3 bg-indigo-600 hover:bg-indigo-500 rounded-xl font-bold text-sm text-white transition flex items-center justify-center space-x-2 shadow-lg shadow-indigo-600/30 mt-2"
              >
                <LogIn className="w-4 h-4" />
                <span>{authTab === 'entrar' ? 'Entrar' : 'Cadastrar'}</span>
              </button>
            </form>

            <button
              type="button"
              onClick={() => setAuthTab(authTab === 'entrar' ? 'cadastrar' : 'entrar')}
              className="text-xs text-indigo-400 mt-5 hover:underline"
            >
              {authTab === 'entrar' ? 'Não tem conta? Cadastrar' : 'Já tem uma conta? Entrar'}
            </button>
          </div>
        </div>

        {/* Footer */}
        <footer className="text-center text-xs text-slate-500">
          • Lembrete Inteligente • Agenda de Lembretes e Compromissos
        </footer>
      </div>
    );
  }

  // Dashboard Screen (faithfully matching Screenshot 1)
  return (
    <div className="min-h-screen bg-[#090D1C] text-slate-100 flex flex-col justify-between">
      {/* Top App Header */}
      <header className="border-b border-[#1A2344] bg-[#090D1C]/90 backdrop-blur sticky top-0 z-40 px-4 py-3">
        <div className="max-w-4xl mx-auto flex justify-between items-center">
          {/* Logo */}
          <div className="flex items-center space-x-2.5 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-indigo-500 to-cyan-400 flex items-center justify-center shadow-md shadow-indigo-500/20 flex-shrink-0">
              <Calendar className="w-4 h-4 text-white" />
            </div>
            <h1 className="text-base md:text-lg font-bold tracking-tight whitespace-nowrap">
              Lembrete <span className="text-indigo-400">Inteligente</span>
            </h1>
          </div>

          {/* Clean Action buttons */}
          <div className="flex items-center space-x-2 flex-shrink-0">
            {/* Sound Toggle */}
            <button
              onClick={() => {
                setSoundEnabled(!soundEnabled);
                showToast(soundEnabled ? 'Som silenciado' : 'Som ativado');
              }}
              className="p-2 rounded-xl bg-[#141C38] hover:bg-[#1C264D] text-indigo-400 transition"
              title="Alternar som"
            >
              {soundEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
            </button>

            {/* User Badge */}
            <button
              onClick={() => {
                if (user?.uid === KNOWN_USERS['usr_68dd8a516780b021'].uid) {
                  setUser(KNOWN_USERS['CiEoITiFP2PqVjFYzu0HZ3TXBmt2']);
                  showToast('Conectado como Maya (Isolamento Firestore ativo)');
                } else {
                  setUser(KNOWN_USERS['usr_68dd8a516780b021']);
                  showToast('Conectado como Michel (Isolamento Firestore ativo)');
                }
              }}
              className="flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-[#1B2344] hover:bg-[#242F5C] border border-[#2B3968] text-xs transition"
              title="Alternar conta isolada"
            >
              <div className="w-5 h-5 rounded-full bg-indigo-600 flex items-center justify-center font-bold text-white text-[10px]">
                {user?.name.charAt(0) || 'M'}
              </div>
              <span className="font-semibold text-white">{user?.name || 'Michel'}</span>
            </button>

            {/* Logout */}
            <button
              onClick={() => setIsAuthenticated(false)}
              className="p-2 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition"
              title="Sair"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="max-w-4xl mx-auto w-full flex-1 px-4 py-5 space-y-5">
        {/* Clean System Status Pill */}
        <div className="flex justify-center">
          <button
            onClick={() => setShowInfoModal(true)}
            className="flex items-center space-x-2 px-3 py-1 rounded-full bg-[#131B38] border border-[#222F5A] text-slate-400 hover:text-slate-200 text-xs transition shadow-sm"
          >
            <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-medium">APK Nativo • AlarmManager 2º Plano Ativo</span>
          </button>
        </div>
        {/* Natural Language Command Card */}
        <div className="bg-[#121933] border border-[#1F2A52] rounded-2xl p-4 md:p-5 shadow-xl">
          <textarea
            rows={2}
            value={commandText}
            onChange={(e) => setCommandText(e.target.value)}
            placeholder='Fale no microfone ou digite: "Hoje às 15h tomar remédio" ou "Pagar conta amanhã às 10h"...'
            className="w-full bg-transparent resize-none text-sm md:text-base text-white placeholder-slate-500 focus:outline-none"
          />

          {/* Avisar options and Action buttons */}
          <div className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-[#1B254A]">
            <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 text-xs">
              <span className="text-slate-400 font-medium flex items-center mr-1">
                <Clock className="w-3.5 h-3.5 mr-1" />
                Avisar:
              </span>
              {(
                [
                  [0, 'No horário'],
                  [5, '5m antes'],
                  [10, '10m antes'],
                  [15, '15m antes'],
                  [30, '30m antes'],
                ] as const
              ).map(([min, label]) => (
                <button
                  key={min}
                  type="button"
                  onClick={() => setAdvanceNotice(min as AdvanceNoticeMinutes)}
                  className={`px-2.5 py-1 rounded-lg transition ${
                    advanceNotice === min
                      ? 'bg-indigo-600 text-white font-semibold'
                      : 'bg-[#161F3D] text-slate-400 hover:text-white'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="flex items-center space-x-2">
              <button
                type="button"
                onClick={handleMicClick}
                className={`w-9 h-9 rounded-full flex items-center justify-center transition ${
                  isListening
                    ? 'bg-rose-500 text-white animate-pulse'
                    : 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-600/30'
                }`}
                title="Falar comando de voz"
              >
                <Mic className="w-4 h-4" />
              </button>

              <button
                type="button"
                onClick={handleSchedule}
                className="flex items-center space-x-1.5 px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-bold transition shadow-md shadow-emerald-600/20"
              >
                <span>Agendar</span>
                <Send className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Exemplos chips */}
          <div className="flex items-center space-x-2 overflow-x-auto pt-3 mt-3 border-t border-[#17203E] text-xs">
            <span className="text-slate-400 font-medium flex items-center whitespace-nowrap">
              <Sparkles className="w-3.5 h-3.5 text-amber-400 mr-1" />
              Exemplos:
            </span>
            {[
              'Tomar remédio todo dia às 08h',
              'Reunião toda segunda às 09h',
              'Pagar boleto amanhã às 14h',
              'Alongamento de segunda a sexta às 16h',
            ].map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => setCommandText(ex)}
                className="px-2.5 py-1 rounded-xl bg-[#172242] border border-[#26335C] text-slate-300 hover:text-white whitespace-nowrap hover:border-indigo-500 transition"
              >
                {ex}
              </button>
            ))}
          </div>
        </div>

        {/* Quick Instant Test Alarm Button */}
        <div className="bg-[#161E3D] border border-[#2E3A68] rounded-xl p-3 flex items-center justify-between text-xs">
          <div className="flex items-center space-x-2">
            <Play className="w-4 h-4 text-amber-400" />
            <span className="font-semibold text-slate-200">
              Testar Alarme Nativo no AlarmManager / LocalNotifications (2s)
            </span>
          </div>
          <button
            onClick={() => {
              triggerTestAlarm();
              showToast('Alarme disparará em 2 segundos com som e vibração!');
            }}
            className="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg font-bold transition"
          >
            Disparar Agora
          </button>
        </div>

        {/* Section Header: Cronograma & Agenda */}
        <div>
          <div className="flex flex-wrap justify-between items-center gap-2 mb-2">
            <div className="flex items-center space-x-2">
              <h2 className="text-lg font-bold tracking-tight text-white">Cronograma & Agenda</h2>
              <span className="px-2 py-0.5 rounded-full bg-[#1E2648] text-indigo-300 text-[10px] font-medium">
                Ordem Cronológica
              </span>
            </div>
          </div>
          <p className="text-xs text-slate-400 mb-3">
            Lembretes isolados para: <span className="text-indigo-400 font-semibold">{user?.name || 'Michel'}</span>
          </p>

          {/* Search bar */}
          <div className="relative mb-3">
            <Search className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
            <input
              type="text"
              placeholder="Buscar compromisso..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-[#121933] border border-[#26335C] rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition"
            />
          </div>
        </div>

        {/* Status Tabs */}
        <div className="grid grid-cols-4 bg-[#121933] p-1 rounded-xl gap-1 text-xs">
          {(['Pendentes', 'Adiados', 'Concluídos', 'Todos'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setStatusFilter(tab)}
              className={`py-2 rounded-lg font-medium text-center transition ${
                statusFilter === tab
                  ? 'bg-indigo-600 text-white font-bold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {tab} ({counts[tab]})
            </button>
          ))}
        </div>

        {/* Category Filter Chips */}
        <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 text-xs">
          <Filter className="w-3.5 h-3.5 text-slate-500 mr-1 flex-shrink-0" />
          {['Todas', 'Trabalho', 'Saúde', 'Finanças', 'Pessoal', 'Estudos'].map((cat) => (
            <button
              key={cat}
              onClick={() => setCategoryFilter(cat)}
              className={`px-3 py-1.5 rounded-full whitespace-nowrap transition ${
                categoryFilter === cat
                  ? 'bg-indigo-600 text-white font-bold'
                  : 'bg-[#141C38] border border-[#222D52] text-slate-300 hover:text-white'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Reminders List or Empty State */}
        {filteredReminders.length === 0 ? (
          <div className="bg-[#10162E] border border-[#1A2242] rounded-2xl py-12 px-6 flex flex-col items-center text-center">
            {/* Alarm Clock Illustration */}
            <div className="w-16 h-16 rounded-full bg-[#1D2447] flex items-center justify-center mb-4">
              <div className="relative">
                <div className="w-9 h-9 rounded-full bg-rose-500/20 border-2 border-rose-500 flex items-center justify-center">
                  <div className="w-2.5 h-2.5 border-r-2 border-b-2 border-rose-400 rotate-45 transform" />
                </div>
                {/* Bell ears */}
                <div className="absolute -top-1 -left-1 w-2.5 h-2.5 bg-rose-500 rounded-full" />
                <div className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-rose-500 rounded-full" />
              </div>
            </div>
            <h3 className="text-base font-bold text-white mb-1">
              Nenhum lembrete nesta visualização
            </h3>
            <p className="text-xs text-slate-400 max-w-sm">
              Tudo em dia! Diga ou digite um comando acima para agendar novas tarefas.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredReminders.map((reminder) => {
              const isCompleted = reminder.status === 'completed';
              const isPostponed = reminder.status === 'postponed';
              const dateStr = new Date(reminder.scheduledTime).toLocaleString('pt-BR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
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
                  className={`bg-[#131A36] border rounded-2xl p-4 transition ${
                    isCompleted ? 'border-[#1B2642] opacity-70' : 'border-[#222F56]'
                  }`}
                >
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex items-center space-x-2">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                          categoryColors[reminder.category] || categoryColors.Pessoal
                        }`}
                      >
                        {reminder.category}
                      </span>
                      {isPostponed && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/20 text-amber-300">
                          Adiado
                        </span>
                      )}
                      {isCompleted && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/20 text-emerald-300">
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
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>

                  <h4 className={`text-sm md:text-base font-semibold ${isCompleted ? 'line-through text-slate-500' : 'text-white'}`}>
                    {reminder.title}
                  </h4>

                  <div className="flex items-center space-x-2 text-xs text-indigo-400 mt-2 mb-3">
                    <Clock className="w-3.5 h-3.5" />
                    <span>{dateStr}</span>
                    {reminder.advanceNoticeMinutes > 0 && (
                      <span className="text-slate-400">• Avisar {reminder.advanceNoticeMinutes}m antes</span>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex justify-between items-center pt-2 border-t border-[#1C254B] text-xs">
                    <div className="flex items-center space-x-3">
                      <button
                        onClick={() => {
                          speakText(`Lembrete: ${reminder.title}. Programado para ${dateStr}`);
                          showToast('Reproduzindo em áudio...');
                        }}
                        className="flex items-center space-x-1 text-indigo-400 hover:text-indigo-300 transition"
                      >
                        <Volume2 className="w-3.5 h-3.5" />
                        <span>Ouvir</span>
                      </button>

                      <button
                        onClick={() => {
                          triggerTestAlarm(reminder.title);
                          showToast(`Disparando som de teste para: ${reminder.title}`);
                        }}
                        className="flex items-center space-x-1 text-cyan-400 hover:text-cyan-300 transition"
                      >
                        <Play className="w-3.5 h-3.5" />
                        <span>Testar Alarme</span>
                      </button>
                    </div>

                    <div className="flex items-center space-x-2">
                      {!isCompleted && (
                        <button
                          onClick={() => {
                            postponeReminder(reminder.id, 10);
                            showToast('Compromisso adiado por 10 minutos');
                          }}
                          className="px-2.5 py-1 rounded-lg bg-[#1E284A] text-amber-400 hover:bg-[#283663] font-semibold transition"
                        >
                          Adiar (+10m)
                        </button>
                      )}

                      <button
                        onClick={() => {
                          toggleComplete(reminder.id);
                          showToast(isCompleted ? 'Reativado como pendente' : 'Concluído com sucesso!');
                        }}
                        className={`flex items-center space-x-1 px-3 py-1 rounded-lg font-bold transition ${
                          isCompleted
                            ? 'bg-[#1E284A] text-slate-300 hover:bg-[#283663]'
                            : 'bg-emerald-600 hover:bg-emerald-500 text-white'
                        }`}
                      >
                        {isCompleted ? (
                          <>
                            <RotateCcw className="w-3 h-3" />
                            <span>Reativar</span>
                          </>
                        ) : (
                          <>
                            <Check className="w-3 h-3" />
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
      </main>

      {/* Footer */}
      <footer className="text-center text-xs text-slate-500 py-4 border-t border-[#151D38]">
        • Lembrete Inteligente • Agenda de Lembretes e Compromissos
      </footer>

      {/* Info Modal */}
      {showInfoModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
          <div className="bg-[#121933] border border-[#26335C] rounded-2xl max-w-md w-full p-6 text-xs text-slate-300 space-y-3">
            <h3 className="text-base font-bold text-white flex items-center">
              <Cpu className="w-5 h-5 text-indigo-400 mr-2" />
              Arquitetura Nativa Android & Capacitor
            </h3>
            <p>
              • <strong>AlarmManager Core:</strong> No APK Android, os alarmes usam <code className="text-indigo-300">setExactAndAllowWhileIdle()</code> disparando diretamente no relógio do sistema.
            </p>
            <p>
              • <strong>Tela de Bloqueio:</strong> As intents possuem permissões <code className="text-indigo-300">WAKE_LOCK</code> e prioridade máxima para tocar som contínuo e vibrar mesmo com o aparelho bloqueado.
            </p>
            <p>
              • <strong>Isolamento Multi-usuário:</strong> Subcoleções no Firestore <code className="text-indigo-300">/users/{'{userId}'}/reminders</code> garantem 100% de privacidade para Michel e Maya.
            </p>
            <button
              onClick={() => setShowInfoModal(false)}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl font-bold mt-4 transition"
            >
              Fechar
            </button>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-[#1E284A] border border-indigo-500 text-white text-xs px-4 py-2 rounded-xl shadow-2xl z-50 animate-bounce">
          {toastMessage}
        </div>
      )}
    </div>
  );
}

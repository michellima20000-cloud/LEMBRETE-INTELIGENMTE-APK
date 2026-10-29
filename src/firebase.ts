import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyCSxt9VNzPwLNDv9tCZpZbnXszgbT72fps",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "lembrete-inteligente-b6cd1.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "lembrete-inteligente-b6cd1",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "lembrete-inteligente-b6cd1.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "408773274044",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:408773274044:android:578bcdeab13c0c102463b9",
};

// Initialize Firebase once
export const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
export const auth = getAuth(app);
export const db = getFirestore(app);

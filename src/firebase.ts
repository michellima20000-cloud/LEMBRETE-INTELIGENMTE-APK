import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyCSxt9VNzPwLNDv9tCZpZbnXszgbT72fps",
  authDomain: "lembrete-inteligente-b6cd1.firebaseapp.com",
  projectId: "lembrete-inteligente-b6cd1",
  storageBucket: "lembrete-inteligente-b6cd1.firebasestorage.app",
  messagingSenderId: "408773274044",
  appId: "1:408773274044:android:578bcdeab13c0c102463b9",
};

// Initialize Firebase once
export const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
export const auth = getAuth(app);
export const db = getFirestore(app);

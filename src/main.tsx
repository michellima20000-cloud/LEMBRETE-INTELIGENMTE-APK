import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
import { RemindersProvider } from './context/RemindersContext';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RemindersProvider>
      <App />
    </RemindersProvider>
  </React.StrictMode>
);

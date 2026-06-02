import React from 'react';
import ReactDOM from 'react-dom/client';
import Dashboard from './Dashboard.jsx';
import './index.css';

// ===== Storage shim =====
// The original artifact saved data through window.storage — an API that only
// exists inside Claude. Here we provide a drop-in replacement backed by the
// browser's localStorage, so Dashboard.jsx keeps working unchanged.
// The API shape matches what the component expects:
//   await window.storage.get(key)  -> { value: string | null }
//   await window.storage.set(key, value)
//   await window.storage.delete(key)
if (typeof window !== 'undefined' && !window.storage) {
  window.storage = {
    get: async (key) => {
      try { return { value: window.localStorage.getItem(key) }; }
      catch (e) { return { value: null }; }
    },
    set: async (key, value) => {
      try { window.localStorage.setItem(key, value); } catch (e) {}
    },
    delete: async (key) => {
      try { window.localStorage.removeItem(key); } catch (e) {}
    },
  };
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Dashboard />
  </React.StrictMode>
);

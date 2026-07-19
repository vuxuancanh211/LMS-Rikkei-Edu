/* Ambient types for the runtime-global module pattern.
   React/ReactDOM are exposed on window by globals.ts before the
   feature modules run; the UI modules register components on window
   (e.g. window.StuDashboard) and read shared helpers off window. */
import type * as ReactNS from 'react';
import type * as ReactDOMClientNS from 'react-dom/client';

declare global {
  // 'allowUmdGlobalAccess' lets modules use the global React from @types/react,
  // but we also declare these for clarity / ReactDOM client root typing.
  const ReactDOM: typeof ReactDOMClientNS;

  interface Window {
    React: typeof ReactNS;
    ReactDOM: typeof ReactDOMClientNS;
    // Feature modules + shared UI helpers are registered here at runtime.
    [key: string]: any;
  }
}

export {};

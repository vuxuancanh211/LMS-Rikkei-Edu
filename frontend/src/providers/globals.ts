// Expose React + ReactDOM as globals so the design modules (which were authored
// against UMD globals) work unchanged under Vite's ES-module bundling.
import React from 'react';
import * as ReactDOMClient from 'react-dom/client';
import { httpClient } from '../lib';
window.React = React;
window.ReactDOM = ReactDOMClient;
window.httpClient = httpClient;

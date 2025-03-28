import React from 'react';
import { AppProvider } from '@shopify/polaris';
import { Provider as AppBridgeProvider } from '@shopify/app-bridge-react';
import '@shopify/polaris/build/esm/styles.css';
import './App.css';
import Routes from './Routes';

const config = {
  apiKey: process.env.REACT_APP_SHOPIFY_API_KEY,
  host: process.env.REACT_APP_SHOPIFY_HOST,
  forceRedirect: true
};

function App() {
  return (
    <AppBridgeProvider config={config}>
      <AppProvider i18n={{}}>
        <Routes />
      </AppProvider>
    </AppBridgeProvider>
  );
}

export default App;

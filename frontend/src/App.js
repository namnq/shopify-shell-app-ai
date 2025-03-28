import React from 'react';
import { AppProvider } from '@shopify/polaris';
import { Provider as AppBridgeProvider } from '@shopify/app-bridge-react';
import '@shopify/polaris/build/esm/styles.css';
import { BrowserRouter } from 'react-router-dom';
import Routes from './Routes';

function App() {
  const config = {
    apiKey: process.env.REACT_APP_SHOPIFY_API_KEY,
    host: new URLSearchParams(window.location.search).get('host'),
    forceRedirect: true
  };

  return (
    <AppBridgeProvider config={config}>
      <AppProvider i18n={{}}>
        <BrowserRouter>
          <Routes />
        </BrowserRouter>
      </AppProvider>
    </AppBridgeProvider>
  );
}

export default App;
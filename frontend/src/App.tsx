import React, { useMemo } from 'react';
import { AppProvider } from '@shopify/polaris';
import { Provider as AppBridgeProvider } from '@shopify/app-bridge-react';
import { BrowserRouter } from 'react-router-dom';
import '@shopify/polaris/build/esm/styles.css';
import './App.css';
import Routes from './Routes';

function App() {
  // Extract host and shop parameters with proper fallbacks
  const searchParams = new URLSearchParams(window.location.search);
  const host = searchParams.get('host');
  const shop = searchParams.get('shop');
  console.log(window.location)
  // Create the AppBridge config with all required parameters
  const appBridgeConfig = useMemo(() => ({
    apiKey: process.env.REACT_APP_SHOPIFY_API_KEY || '',
    host: host || '',
    forceRedirect: true
  }), [host]);

  console.log('AppBridge Config:', appBridgeConfig);

  // If missing host parameter, redirect to auth
  if (!host && shop) {
    const authUrl = `/auth/login?shop=${shop}`;
    console.log('Missing host param, redirecting to:', authUrl);
    window.location.href = authUrl;
    return <div>Redirecting to auth...</div>;
  }

  return (
    <BrowserRouter>
      <AppBridgeProvider config={appBridgeConfig}>
        <AppProvider i18n={{}}>
          <Routes />
        </AppProvider>
      </AppBridgeProvider>
    </BrowserRouter>
  );
}

export default App;
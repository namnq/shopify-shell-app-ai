import React from 'react';
import { createContext } from 'react';
import { Provider as AppBridgeProvider } from '@shopify/app-bridge-react';
import { AppProvider as PolarisProvider } from '@shopify/polaris';

export const ShopifyContext = createContext(null);

interface ShopifyProviderProps {
  children: React.ReactNode;
  config: any; // Consider defining a proper type for App Bridge config
}

export function ShopifyProvider({ children, config }: ShopifyProviderProps) {
  return (
    <PolarisProvider i18n={{}}>
      <AppBridgeProvider config={config}>
        {children}
      </AppBridgeProvider>
    </PolarisProvider>
  );
}
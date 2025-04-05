import React from 'react';
import { Page, Layout, Card } from '@shopify/polaris';

export default function HomePage() {
  return (
    <Page title="Shopify App Dashboard">
      <Layout>
        <Layout.Section>
          <Card title="Welcome" sectioned>
            <p>Your embedded Shopify app is running successfully!</p>
          </Card>
        </Layout.Section>
      </Layout>
    </Page>
  );
}
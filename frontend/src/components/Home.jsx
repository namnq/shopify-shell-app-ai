import React from 'react';
import { Page, Layout, Card } from '@shopify/polaris';

const Home = () => {
  return (
    <Page title="Shopify App">
      <Layout>
        <Layout.Section>
          <Card title="Welcome" sectioned>
            <p>Your Shopify app is successfully running!</p>
          </Card>
        </Layout.Section>
      </Layout>
    </Page>
  );
};

export default Home;
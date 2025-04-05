import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Page, Layout, Card, Spinner } from '@shopify/polaris';
import { useAppBridge } from '@shopify/app-bridge-react';
import { getSessionToken } from '@shopify/app-bridge-utils';

export default function AuthPage() {
  const app = useAppBridge();
  const navigate = useNavigate();

  useEffect(() => {
    async function verifyAuth() {
      try {
        const token = await getSessionToken(app);
        // Store token and redirect to home
        localStorage.setItem('shopify_token', token);
        navigate('/');
      } catch (error) {
        // Redirect to install if not authenticated
        window.location.href = `/auth/install?shop=${new URLSearchParams(window.location.search).get('shop')}`;
      }
    }
    verifyAuth();
  }, [app, navigate]);

  return (
    <Page title="Authenticating...">
      <Layout>
        <Layout.Section>
          <Card sectioned>
            <div style={{ textAlign: 'center' }}>
              <Spinner size="large" />
              <p>Verifying authentication...</p>
            </div>
          </Card>
        </Layout.Section>
      </Layout>
    </Page>
  );
}
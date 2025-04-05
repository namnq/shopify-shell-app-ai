import { Routes as RouterRoutes, Route } from 'react-router-dom';
   import { AppProvider } from '@shopify/polaris';
   import HomePage from './pages/HomePage';
   import AuthPage from './pages/AuthPage';
   
   export default function Routes() {
     return (
       <AppProvider i18n={{}}>
         <RouterRoutes>
           <Route path="/" element={<HomePage />} />
           <Route path="/auth" element={<AuthPage />} />
         </RouterRoutes>
       </AppProvider>
     );
   }
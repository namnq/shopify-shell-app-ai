# Shopify Embedded App with AI Capabilities

## 🚀 Tech Stack
- **Backend**: Java 21 + Spring Boot 3
- **Frontend**: React + Shopify App Bridge
- **Build**: Maven + npm
- **Authentication**: Shopify OAuth 2.0

## 📂 Project Structure
```
shopify-app/
├── frontend/      # React frontend (Polaris UI)
├── src/           # Spring Boot backend
│   ├── main/java/com/shopify/
│   └── resources/
├── target/        # Build outputs
└── pom.xml        # Maven config
```

## 🔧 Setup Guide

### Backend
```bash
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm start
```

## 🔐 OAuth Flow
1. Frontend redirects to `/auth/install?shop=your-store.myshopify.com`
2. Backend handles Shopify OAuth handshake
3. Merchant approves permissions
4. Shopify redirects with access token

## 🌐 Deployment
Configure these environment variables:
```env
SHOPIFY_API_KEY=your_api_key
SHOPIFY_API_SECRET=your_api_secret
REACT_APP_BACKEND_URL=your_backend_url
```